package com.finai.service.impl;

import com.finai.model.AnalysisTask;
import com.finai.model.Evidence;
import com.finai.model.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finai.model.AnalysisSnapshot;
import com.finai.repository.AnalysisSnapshotRepository;
import com.finai.repository.AnalysisTaskRepository;
import com.finai.repository.EvidenceRepository;
import com.finai.service.AnalysisService;
import com.finai.service.AuditLogService;
import com.finai.service.TaskExecutionWorker;
import com.finai.agent.TaskOrchestrator;
import com.finai.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 分析服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {

    private final AnalysisTaskRepository taskRepository;
    private final EvidenceRepository evidenceRepository;
    private final AnalysisSnapshotRepository snapshotRepository;
    private final AuditLogService auditLogService;
    private final TaskExecutionWorker taskExecutionWorker;
    private final ObjectMapper objectMapper;
    private final TaskOrchestrator orchestrator;
    private final com.finai.repository.ValuationConfirmationRepository valuationConfirmations;

    @Override
    @Transactional
    public TaskResponseDTO createTask(TaskRequestDTO request, MultipartFile file) {
        log.info("Creating analysis task for company: {}", request.getCompanyCode());

        if (file == null || file.isEmpty() || file.getSize() > 50L * 1024 * 1024) {
            throw new IllegalArgumentException("请上传不超过 50MB 的 PDF 财报");
        }
        try (java.io.InputStream input = file.getInputStream()) {
            if (!java.util.Arrays.equals(input.readNBytes(5), "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("文件内容不是 PDF");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取上传的 PDF", e);
        }

        // 生成任务ID
        String taskId = "TASK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        // 创建任务实体
        AnalysisTask task = AnalysisTask.builder()
                .taskId(taskId)
                .companyCode(request.getCompanyCode())
                .companyName(request.getCompanyName())
                .reportPeriod(request.getReportPeriod())
                .analysisType(request.getAnalysisType())
                .status(AnalysisTask.TaskStatus.CREATED)
                .progress(0)
                .createdAt(LocalDateTime.now())
                .build();

        // 处理文件上传
        if (file != null && !file.isEmpty()) {
            try {
                String uploadPath = saveUploadedFile(file, taskId);
                task.setUploadedFilePath(uploadPath);
                log.info("File uploaded: {}", uploadPath);
            } catch (IOException e) {
                log.error("Failed to save uploaded file", e);
                throw new RuntimeException("Failed to save uploaded file", e);
            }
        }

        // 保存任务
        task = taskRepository.save(task);

        // 记录审计日志
        auditLogService.logTaskCreation(taskId, request);

        String pipelineTaskId = task.getTaskId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    taskExecutionWorker.execute(pipelineTaskId);
                }
            });
        } else {
            taskExecutionWorker.execute(pipelineTaskId);
        }

        return TaskResponseDTO.fromEntity(task);
    }

    @Override
    public TaskResponseDTO getTask(String taskId) {
        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        return TaskResponseDTO.fromEntity(task);
    }

    @Override
    public List<TaskResponseDTO> listTasks(String companyCode, AnalysisTask.TaskStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<AnalysisTask> tasksPage;

        if (companyCode != null && status != null) {
            tasksPage = taskRepository.findByCompanyCodeAndStatusOrderByCreatedAtDesc(companyCode, status, pageable);
        } else if (companyCode != null) {
            tasksPage = taskRepository.findByCompanyCodeOrderByCreatedAtDesc(companyCode, pageable);
        } else if (status != null) {
            tasksPage = taskRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            tasksPage = taskRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return tasksPage.getContent().stream()
                .map(TaskResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public AnalysisReportDTO getReport(String taskId) {
        return readSnapshot(taskId).report();
    }

    @Override
    public FinancialMetricsDTO getMetrics(String taskId) {
        return readSnapshot(taskId).metrics();
    }

    @Override
    public List<AnomalySignalDTO> getAnomalies(String taskId) {
        return readSnapshot(taskId).anomalies();
    }

    @Override
    public ValuationResultDTO getValuation(String taskId) {
        return readSnapshot(taskId).valuation();
    }

    @Override
    public List<EvidenceDTO> getEvidence(String taskId, String fieldId) {
        List<Evidence> evidenceList;

        if (fieldId != null) {
            evidenceList = evidenceRepository.findByTaskIdAndFieldId(taskId, fieldId);
        } else {
            evidenceList = evidenceRepository.findByTaskIdOrderByFieldId(taskId);
        }

        return evidenceList.stream()
                .map(EvidenceDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void cancelTask(String taskId) {
        AnalysisTask task = taskRepository.findLocked(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        if (task.getStatus() == AnalysisTask.TaskStatus.RUNNING ||
            task.getStatus() == AnalysisTask.TaskStatus.QUEUED) {
            task.setStatus(AnalysisTask.TaskStatus.CANCELLED);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);

            auditLogService.logTaskCancellation(taskId);
            log.info("Task cancelled: {}", taskId);
        }
    }

    @Override
    @Transactional
    public void deleteTask(String taskId) {
        AnalysisTask task = taskRepository.findLocked(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        if (task.getStatus() == AnalysisTask.TaskStatus.RUNNING || task.getStatus() == AnalysisTask.TaskStatus.QUEUED) {
            throw new IllegalStateException("Cancel the running task before deletion");
        }

        // 删除相关证据
        evidenceRepository.deleteByTaskId(taskId);

        snapshotRepository.deleteByTaskId(taskId);
        orchestrator.delete(taskId);
        valuationConfirmations.deleteByTaskId(taskId);
        taskRepository.delete(task);

        auditLogService.logTaskDeletion(taskId);
        log.info("Task deleted: {}", taskId);
    }

    @Override
    @Transactional
    public void executeTask(String taskId) {
        AnalysisTask task = taskRepository.findLocked(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        if (task.getStatus() == AnalysisTask.TaskStatus.RUNNING || task.getStatus() == AnalysisTask.TaskStatus.QUEUED) {
            throw new IllegalStateException("Task is already running");
        }
        if (task.getUploadedFilePath() == null || !Files.isRegularFile(Path.of(task.getUploadedFilePath()))) {
            throw new IllegalStateException("Uploaded statement is missing");
        }
        task.setStatus(AnalysisTask.TaskStatus.QUEUED);
        task.setProgress(0);
        task.setErrorMessage(null);
        taskRepository.save(task);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskExecutionWorker.execute(taskId);
            }
        });
    }

    private LoadedSnapshot readSnapshot(String taskId) {
        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        if (task.getStatus() != AnalysisTask.TaskStatus.COMPLETED) {
            throw new IllegalStateException("Task is not completed yet");
        }
        AnalysisSnapshot snapshot = snapshotRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalStateException("分析结果不存在"));
        try {
            FinancialMetricsDTO metrics = objectMapper.readValue(snapshot.getMetricsJson(), FinancialMetricsDTO.class);
            List<AnomalySignalDTO> anomalies = objectMapper.readValue(snapshot.getAnomaliesJson(), new TypeReference<>() {});
            ValuationResultDTO valuation = snapshot.getValuationJson() == null
                    ? null
                    : objectMapper.readValue(snapshot.getValuationJson(), ValuationResultDTO.class);
            AnalysisReportDTO report = objectMapper.readValue(snapshot.getReportJson(), AnalysisReportDTO.class);
            return new LoadedSnapshot(metrics, anomalies, valuation, report);
        } catch (Exception e) {
            log.error("Snapshot read failed for task {}: {}", taskId, e.getClass().getSimpleName());
            throw new IllegalStateException("分析结果无法读取", e);
        }
    }

    private record LoadedSnapshot(FinancialMetricsDTO metrics, List<AnomalySignalDTO> anomalies,
                                  ValuationResultDTO valuation, AnalysisReportDTO report) {
    }

    /**
     * 保存上传的文件
     */
    private String saveUploadedFile(MultipartFile file, String taskId) throws IOException {
        Path uploadDir = Paths.get("data", "uploads").toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("仅支持 PDF 文件");
        }
        String filename = taskId + ".pdf";

        Path filepath = uploadDir.resolve(filename);
        file.transferTo(filepath);

        return filepath.toString();
    }
}

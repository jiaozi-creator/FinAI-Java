package com.finai.service;

import com.finai.model.AnalysisTask;
import com.finai.model.dto.TaskRequestDTO;
import com.finai.model.dto.TaskResponseDTO;
import com.finai.repository.AnalysisSnapshotRepository;
import com.finai.repository.AnalysisTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;

/**
 * 内置样例。底稿是资源文件里的报表文本，结果仍由计算链路生成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoTaskService {

    public static final String TASK_ID = "TASK_DEMO_JINGXING";
    public static final String COMPANY_CODE = "DEMO01";

    private final AnalysisTaskRepository taskRepository;
    private final AnalysisSnapshotRepository snapshotRepository;
    private final TaskExecutionWorker taskExecutionWorker;
    private final AuditLogService auditLogService;

    public TaskResponseDTO open() {
        if (isReady()) {
            return TaskResponseDTO.fromEntity(taskRepository.findById(TASK_ID).orElseThrow());
        }
        Path statement = writeSample();
        AnalysisTask task = taskRepository.findById(TASK_ID).orElseGet(() -> AnalysisTask.builder()
                .taskId(TASK_ID)
                .companyCode(COMPANY_CODE)
                .companyName("景行示范（内置样例）")
                .reportPeriod("2024A")
                .analysisType(AnalysisTask.AnalysisType.FULL)
                .status(AnalysisTask.TaskStatus.CREATED)
                .progress(0)
                .createdAt(LocalDateTime.now())
                .build());
        task.setCompanyCode(COMPANY_CODE);
        task.setCompanyName("景行示范（内置样例）");
        task.setReportPeriod("2024A");
        task.setAnalysisType(AnalysisTask.AnalysisType.FULL);
        task.setUploadedFilePath(statement.toString());
        task.setStatus(AnalysisTask.TaskStatus.CREATED);
        task.setErrorMessage(null);
        task.setProgress(0);
        taskRepository.save(task);
        auditLogService.logTaskCreation(TASK_ID, TaskRequestDTO.builder()
                .companyCode(COMPANY_CODE)
                .companyName(task.getCompanyName())
                .reportPeriod("2024A")
                .analysisType(AnalysisTask.AnalysisType.FULL)
                .build());
        taskExecutionWorker.runPipeline(TASK_ID);
        return TaskResponseDTO.fromEntity(taskRepository.findById(TASK_ID).orElseThrow());
    }

    private boolean isReady() {
        return taskRepository.findById(TASK_ID)
                .filter(task -> task.getStatus() == AnalysisTask.TaskStatus.COMPLETED)
                .filter(task -> snapshotRepository.findByTaskId(TASK_ID).isPresent())
                .isPresent();
    }

    private Path writeSample() {
        try {
            Path dir = Path.of("./data/uploads");
            Files.createDirectories(dir);
            Path target = dir.resolve(TASK_ID + ".txt");
            ClassPathResource resource = new ClassPathResource("samples/demo-statement.txt");
            try (InputStream input = resource.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (Exception e) {
            throw new IllegalStateException("内置样例报表无法写入", e);
        }
    }

    @Component
    @Order(20)
    @RequiredArgsConstructor
    static class SeedOnStartup implements CommandLineRunner {
        private final DemoTaskService demoTaskService;

        @Override
        public void run(String... args) {
            try {
                demoTaskService.open();
                log.info("Demo task ready: {}", TASK_ID);
            } catch (Exception e) {
                log.warn("Demo task was not seeded: {}", e.getMessage());
            }
        }
    }
}

package com.finai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finai.analysis.AnomalyRuleEngine;
import com.finai.analysis.ArticulationCheck;
import com.finai.analysis.MetricCalculator;
import com.finai.analysis.ReportComposer;
import com.finai.analysis.StatementExtract;
import com.finai.analysis.StatementExtractor;
import com.finai.analysis.ValuationEngine;
import com.finai.model.AnalysisSnapshot;
import com.finai.model.AnalysisTask;
import com.finai.model.dto.AnalysisReportDTO;
import com.finai.model.dto.AnomalySignalDTO;
import com.finai.model.dto.FinancialMetricsDTO;
import com.finai.model.dto.ValuationResultDTO;
import com.finai.repository.AnalysisSnapshotRepository;
import com.finai.repository.AnalysisTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 独立 bean，保证 @Async 不被同类自调用绕过。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutionWorker {

    private final AnalysisTaskRepository taskRepository;
    private final EvidenceStore evidenceStore;
    private final AnalysisSnapshotRepository snapshotRepository;
    private final AuditLogService auditLogService;
    private final StatementExtractor statementExtractor;
    private final MetricCalculator metricCalculator;
    private final AnomalyRuleEngine anomalyRuleEngine;
    private final ValuationEngine valuationEngine;
    private final ReportComposer reportComposer;
    private final ObjectMapper objectMapper;

    @Async
    public void execute(String taskId) {
        runPipeline(taskId);
    }

    public void runPipeline(String taskId) {
        long startedAt = System.currentTimeMillis();
        log.info("Pipeline start: {}", taskId);
        try {
            AnalysisTask task = load(taskId);
            if (task.getStatus() == AnalysisTask.TaskStatus.CANCELLED) {
                return;
            }
            markRunning(task);
            if (stopped(taskId)) {
                return;
            }

            update(taskId, 15, "解析财报");
            long parseStart = System.currentTimeMillis();
            if (task.getUploadedFilePath() == null || !java.nio.file.Files.exists(java.nio.file.Path.of(task.getUploadedFilePath()))) {
                throw new IllegalStateException("没有读到上传的财报文件");
            }
            StatementExtract extract = statementExtractor.extract(task.getUploadedFilePath());
            auditLogService.logToolInvocation(taskId, "pdf.parse",
                    String.valueOf(task.getUploadedFilePath()),
                    "lines=" + extract.getLines().size() + ",policyNotes=" + extract.getPolicyNotes().size(),
                    System.currentTimeMillis() - parseStart);
            evidenceStore.replace(task, extract);
            auditLogService.logFileAccess(taskId, task.getUploadedFilePath(), extract.getSha256(),
                    java.nio.file.Files.size(java.nio.file.Path.of(task.getUploadedFilePath())),
                    extract.getPageCount() == null ? 0 : extract.getPageCount());
            if (stopped(taskId)) {
                return;
            }

            update(taskId, 40, "计算同比与勾稽");
            long calcStart = System.currentTimeMillis();
            FinancialMetricsDTO metrics = metricCalculator.calculate(extract.getLines(), task.getReportPeriod());
            List<ArticulationCheck> checks = metricCalculator.articulate(extract.getLines());
            auditLogService.logToolInvocation(taskId, "metric.calculate", task.getReportPeriod(),
                    objectMapper.writeValueAsString(metrics), System.currentTimeMillis() - calcStart);
            auditLogService.logToolInvocation(taskId, "articulation.check", task.getTaskId(),
                    objectMapper.writeValueAsString(checks), 0L);
            if (stopped(taskId)) {
                return;
            }

            update(taskId, 60, "异常规则");
            long anomalyStart = System.currentTimeMillis();
            List<AnomalySignalDTO> anomalies = anomalyRuleEngine.detect(extract.getLines(), metrics, extract.getPolicyNotes());
            auditLogService.logToolInvocation(taskId, "anomaly.rules", AnomalyRuleEngine.RULE_VERSION,
                    "count=" + anomalies.size(), System.currentTimeMillis() - anomalyStart);
            if (stopped(taskId)) {
                return;
            }

            update(taskId, 80, "估值建模");
            ValuationResultDTO valuation = null;
            if (task.getAnalysisType() == AnalysisTask.AnalysisType.FULL
                    || task.getAnalysisType() == AnalysisTask.AnalysisType.VALUATION_ONLY) {
                long valueStart = System.currentTimeMillis();
                valuation = valuationEngine.evaluate(extract.getLines(), metrics);
                auditLogService.logToolInvocation(taskId, "valuation.dcf", task.getAnalysisType().name(),
                        valuation.getValuationRange() == null ? "range=absent" : "range=present",
                        System.currentTimeMillis() - valueStart);
            }
            if (stopped(taskId)) {
                return;
            }

            update(taskId, 92, "生成报告");
            AnalysisReportDTO report = reportComposer.compose(task, extract, metrics, checks, anomalies, valuation, startedAt);
            AnalysisSnapshot snapshot = AnalysisSnapshot.builder()
                    .taskId(taskId)
                    .metricsJson(objectMapper.writeValueAsString(metrics))
                    .anomaliesJson(objectMapper.writeValueAsString(anomalies))
                    .valuationJson(valuation == null ? null : objectMapper.writeValueAsString(valuation))
                    .reportJson(objectMapper.writeValueAsString(report))
                    .build();
            snapshotRepository.save(snapshot);

            AnalysisTask done = load(taskId);
            if (done.getStatus() == AnalysisTask.TaskStatus.CANCELLED) {
                return;
            }
            done.setStatus(AnalysisTask.TaskStatus.COMPLETED);
            done.setErrorMessage(null);
            done.setProgress(100);
            done.setCurrentStep("完成");
            done.setCompletedAt(java.time.LocalDateTime.now());
            done.setExecutionTimeMs(System.currentTimeMillis() - startedAt);
            done.setLlmModel(report.getExecutionSummary().getLlmModel());
            done.setLlmCallCount(report.getExecutionSummary().getLlmCallCount());
            taskRepository.save(done);
            auditLogService.logTaskCompletion(taskId);
            log.info("Pipeline done: {}", taskId);
        } catch (Exception e) {
            log.error("Pipeline failed: {}", taskId, e);
            if (stopped(taskId)) {
                return;
            }
            taskRepository.findById(taskId).ifPresent(task -> {
                task.setStatus(AnalysisTask.TaskStatus.FAILED);
                task.setErrorMessage(e.getMessage() == null ? e.getClass().getSimpleName() : truncate(e.getMessage(), 1800));
                task.setCompletedAt(java.time.LocalDateTime.now());
                taskRepository.save(task);
            });
            auditLogService.logTaskFailure(taskId, e);
        }
    }

    private void markRunning(AnalysisTask task) {
        task.setStatus(AnalysisTask.TaskStatus.RUNNING);
        task.setStartedAt(java.time.LocalDateTime.now());
        task.setProgress(10);
        task.setCurrentStep("准备解析");
        taskRepository.save(task);
    }

    private void update(String taskId, int progress, String step) {
        AnalysisTask task = load(taskId);
        if (task.getStatus() == AnalysisTask.TaskStatus.CANCELLED) {
            return;
        }
        task.setStatus(AnalysisTask.TaskStatus.RUNNING);
        task.setProgress(progress);
        task.setCurrentStep(step);
        taskRepository.save(task);
    }

    private boolean stopped(String taskId) {
        return taskRepository.findById(taskId)
                .map(task -> task.getStatus() == AnalysisTask.TaskStatus.CANCELLED)
                .orElse(true);
    }

    private AnalysisTask load(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new com.finai.exception.ResourceNotFoundException("Task not found: " + taskId));
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}

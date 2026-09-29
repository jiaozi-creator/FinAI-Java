package com.finai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finai.analysis.AnomalyRuleEngine;
import com.finai.analysis.ArticulationCheck;
import com.finai.analysis.MetricCalculator;
import com.finai.analysis.ReportComposer;
import com.finai.analysis.StatementExtract;
import com.finai.analysis.StatementExtractor;
import com.finai.analysis.ValuationEngine;
import com.finai.agent.TaskOrchestrator;
import com.finai.model.AnalysisSnapshot;
import com.finai.model.AnalysisTask;
import com.finai.model.dto.AnalysisReportDTO;
import com.finai.model.dto.AnomalySignalDTO;
import com.finai.model.dto.FinancialMetricsDTO;
import com.finai.model.dto.ValuationResultDTO;
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
    private final AuditLogService auditLogService;
    private final StatementExtractor statementExtractor;
    private final MetricCalculator metricCalculator;
    private final AnomalyRuleEngine anomalyRuleEngine;
    private final ValuationEngine valuationEngine;
    private final ReportComposer reportComposer;
    private final ObjectMapper objectMapper;
    private final TaskOrchestrator orchestrator;
    private final TaskStateService taskState;
    private final java.util.Set<String> activeTasks = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Async
    public void execute(String taskId) {
        runPipeline(taskId);
    }

    public void runPipeline(String taskId) {
        if (!activeTasks.add(taskId)) {
            throw new IllegalStateException("Task is already running: " + taskId);
        }
        String runId = null;
        long startedAt = System.currentTimeMillis();
        log.info("Pipeline start: {}", taskId);
        try {
            AnalysisTask task = load(taskId);
            if (!taskState.claim(taskId)) return;
            if (task.getUploadedFilePath() == null || !java.nio.file.Files.exists(java.nio.file.Path.of(task.getUploadedFilePath()))) {
                throw new IllegalStateException("没有读到上传的财报文件");
            }
            runId = orchestrator.start(taskId, task.getUploadedFilePath());
            if (stopped(taskId)) {
                orchestrator.finish(runId, "CANCELLED", null);
                return;
            }

            update(taskId, 15, "解析财报");
            long parseStart = System.currentTimeMillis();
            String executionId = runId;
            StatementExtract extract = orchestrator.stage(executionId, "EVIDENCE", () -> statementExtractor.extract(task.getUploadedFilePath()));
            auditLogService.logToolInvocation(taskId, "pdf.parse",
                    String.valueOf(task.getUploadedFilePath()),
                    "lines=" + extract.getLines().size() + ",policyNotes=" + extract.getPolicyNotes().size(),
                    System.currentTimeMillis() - parseStart);
            evidenceStore.replace(task, extract);
            if (stopped(taskId)) {
                orchestrator.finish(runId, "CANCELLED", null);
                return;
            }

            update(taskId, 40, "计算同比与勾稽");
            long calcStart = System.currentTimeMillis();
            FinancialMetricsDTO metrics = orchestrator.stage(executionId, "FINANCIAL", () -> metricCalculator.calculate(extract.getLines(), task.getReportPeriod()));
            List<ArticulationCheck> checks = orchestrator.stage(executionId, "ARTICULATION", () -> metricCalculator.articulate(extract.getLines()));
            auditLogService.logToolInvocation(taskId, "metric.calculate", task.getReportPeriod(),
                    objectMapper.writeValueAsString(metrics), System.currentTimeMillis() - calcStart);
            auditLogService.logToolInvocation(taskId, "articulation.check", task.getTaskId(),
                    objectMapper.writeValueAsString(checks), 0L);
            if (stopped(taskId)) {
                orchestrator.finish(runId, "CANCELLED", null);
                return;
            }

            update(taskId, 60, "异常规则");
            long anomalyStart = System.currentTimeMillis();
            List<AnomalySignalDTO> anomalies = orchestrator.stage(executionId, "RISK", () -> anomalyRuleEngine.detect(extract.getLines(), metrics, extract.getPolicyNotes()));
            auditLogService.logToolInvocation(taskId, "anomaly.rules", AnomalyRuleEngine.RULE_VERSION,
                    "count=" + anomalies.size(), System.currentTimeMillis() - anomalyStart);
            if (stopped(taskId)) {
                orchestrator.finish(runId, "CANCELLED", null);
                return;
            }

            update(taskId, 80, "估值建模");
            ValuationResultDTO valuation = null;
            if (task.getAnalysisType() == AnalysisTask.AnalysisType.FULL
                    || task.getAnalysisType() == AnalysisTask.AnalysisType.VALUATION_ONLY) {
                long valueStart = System.currentTimeMillis();
                valuation = orchestrator.stage(executionId, "VALUATION", () -> valuationEngine.evaluate(extract.getLines(), metrics));
                auditLogService.logToolInvocation(taskId, "valuation.dcf", task.getAnalysisType().name(),
                        valuation.getValuationRange() == null ? "range=absent" : "range=present",
                        System.currentTimeMillis() - valueStart);
            }
            if (stopped(taskId)) {
                orchestrator.finish(runId, "CANCELLED", null);
                return;
            }

            update(taskId, 92, "生成报告");
            ValuationResultDTO completedValuation = valuation;
            AnalysisReportDTO report = orchestrator.stage(executionId, "REPORT", () -> reportComposer.compose(task, extract, metrics, checks, anomalies, completedValuation, startedAt));
            if (stopped(taskId)) {
                orchestrator.finish(runId, "CANCELLED", null);
                return;
            }
            AnalysisSnapshot snapshot = AnalysisSnapshot.builder()
                    .taskId(taskId)
                    .metricsJson(objectMapper.writeValueAsString(metrics))
                    .anomaliesJson(objectMapper.writeValueAsString(anomalies))
                    .valuationJson(valuation == null ? null : objectMapper.writeValueAsString(valuation))
                    .reportJson(objectMapper.writeValueAsString(report))
                    .build();
            if (!taskState.complete(taskId, snapshot, report.getExecutionSummary().getLlmModel(),
                    report.getExecutionSummary().getLlmCallCount(), System.currentTimeMillis() - startedAt)) {
                orchestrator.finish(runId, "CANCELLED", null);
                return;
            }
            auditLogService.logTaskCompletion(taskId);
            orchestrator.finish(runId, "COMPLETED", null);
            log.info("Pipeline done: {}", taskId);
        } catch (Exception e) {
            log.error("Pipeline failed for {}: {}", taskId, e.getClass().getSimpleName());
            if (stopped(taskId)) {
                orchestrator.finish(runId, "CANCELLED", null);
                return;
            }
            taskState.fail(taskId, e.getClass().getSimpleName());
            auditLogService.logTaskFailure(taskId, e);
            orchestrator.finish(runId, "FAILED", e.getClass().getSimpleName());
        } finally {
            activeTasks.remove(taskId);
        }
    }

    private void update(String taskId, int progress, String step) {
        taskState.progress(taskId, progress, step);
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

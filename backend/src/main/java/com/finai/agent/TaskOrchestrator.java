package com.finai.agent;

import com.finai.analysis.AnomalyRuleEngine;
import com.finai.model.ExecutionRun;
import com.finai.model.ExecutionStage;
import com.finai.repository.AnalysisTaskRepository;
import com.finai.repository.ExecutionRunRepository;
import com.finai.repository.ExecutionStageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 固定阶段的持久化账本，计算仍由 Java 分析服务执行。 */
@Service
@RequiredArgsConstructor
public class TaskOrchestrator {
    public static final List<String> FIXED_STAGES = List.of("EVIDENCE", "FINANCIAL", "ARTICULATION", "RISK", "VALUATION", "REPORT", "REVIEW");
    public static final Set<String> ALLOWED_TOOLS = Set.of("statement.evidence", "statement.metrics", "anomaly.evaluate",
            "valuation.dcf", "valuation.sensitivity", "report.validate");
    private final ExecutionRunRepository runs;
    private final ExecutionStageRepository stages;
    private final AnalysisTaskRepository tasks;

    public String start(String taskId, String filePath) throws Exception {
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(Path.of(filePath))));
        String id = UUID.randomUUID().toString();
        runs.save(ExecutionRun.builder().id(id).taskId(taskId).inputSha256(hash)
                .ruleVersion(AnomalyRuleEngine.RULE_VERSION).status("RUNNING")
                .startedAt(LocalDateTime.now()).build());
        return id;
    }

    public <T> T stage(String runId, String name, StageAction<T> action) throws Exception {
        if (!FIXED_STAGES.contains(name)) {
            throw new IllegalArgumentException("Unsupported pipeline stage: " + name);
        }
        ExecutionRun run = runs.findById(runId).orElseThrow();
        run.setCurrentStage(name);
        runs.save(run);
        ExecutionStage stage = ExecutionStage.builder().id(UUID.randomUUID().toString())
                .runId(runId).name(name).status("RUNNING").startedAt(LocalDateTime.now()).build();
        stages.save(stage);
        try {
            T result = action.run();
            stage.setStatus("COMPLETED");
            stage.setBusinessStatus("COMPUTED");
            if (result instanceof com.finai.model.dto.ValuationResultDTO value) {
                stage.setBusinessStatus(value.getBusinessStatus());
                stage.setBusinessReason(value.getBlockReason());
            } else if (result instanceof AgentResult review) {
                stage.setBusinessStatus("ok".equals(review.getStatus()) ? "PASSED" : "NEEDS_REVIEW");
                stage.setBusinessReason(String.join("；", review.getWarnings()));
                stage.setReviewJson(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(review));
            }
            return result;
        } catch (Exception e) {
            stage.setStatus("FAILED");
            stage.setErrorCode(e.getClass().getSimpleName());
            throw e;
        } finally {
            stage.setFinishedAt(LocalDateTime.now());
            stages.save(stage);
        }
    }

    public void finish(String runId, String status, String errorCode) {
        if (runId == null) return;
        runs.findById(runId).ifPresent(run -> {
            run.setStatus(status);
            run.setErrorCode(errorCode);
            run.setFinishedAt(LocalDateTime.now());
            runs.save(run);
        });
    }

    public List<ExecutionStage> stages(String runId) {
        return stages.findByRunIdOrderByStartedAtAsc(runId);
    }

    public void freezeValuationInputs(String runId, String json) {
        ExecutionRun run = runs.findById(runId).orElseThrow();
        run.setValuationInputsJson(json); runs.save(run);
    }

    public void freezeSnapshot(String runId, String json) {
        ExecutionRun run = runs.findById(runId).orElseThrow();
        run.setSnapshotJson(json); runs.save(run);
    }

    public List<ExecutionRun> runs(String taskId) {
        if (!tasks.existsById(taskId)) throw new com.finai.exception.ResourceNotFoundException("Task not found: " + taskId);
        return runs.findByTaskIdOrderByStartedAtDesc(taskId);
    }

    @Transactional
    public void delete(String taskId) {
        for (ExecutionRun run : runs.findByTaskIdOrderByStartedAtDesc(taskId)) {
            stages.deleteByRunId(run.getId());
        }
        runs.deleteByTaskId(taskId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedRuns() {
        for (ExecutionRun run : runs.findByStatus("RUNNING")) {
            for (ExecutionStage stage : stages.findByRunIdOrderByStartedAtAsc(run.getId())) {
                if ("RUNNING".equals(stage.getStatus())) {
                    stage.setStatus("FAILED");
                    stage.setErrorCode("PROCESS_RESTARTED");
                    stage.setFinishedAt(LocalDateTime.now());
                    stages.save(stage);
                }
            }
            finish(run.getId(), "FAILED", "PROCESS_RESTARTED");
            tasks.findById(run.getTaskId()).ifPresent(task -> {
                if (task.getStatus() == com.finai.model.AnalysisTask.TaskStatus.RUNNING) {
                    task.setStatus(com.finai.model.AnalysisTask.TaskStatus.FAILED);
                    task.setErrorMessage("PROCESS_RESTARTED");
                    tasks.save(task);
                }
            });
        }
    }

    @FunctionalInterface
    public interface StageAction<T> {
        T run() throws Exception;
    }
}

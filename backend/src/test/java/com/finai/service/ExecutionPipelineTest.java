package com.finai.service;

import com.finai.agent.TaskOrchestrator;
import com.finai.model.AnalysisTask;
import com.finai.repository.AnalysisTaskRepository;
import com.finai.repository.AnalysisSnapshotRepository;
import com.finai.repository.ExecutionRunRepository;
import com.finai.repository.ExecutionStageRepository;
import com.finai.model.AnalysisSnapshot;
import com.finai.model.ExecutionStage;
import com.finai.service.impl.AnalysisServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "finai.llm.provider=mock", "finai.dev.mock-llm=true", "finai.dev.demo-enabled=false",
        "spring.datasource.url=jdbc:h2:mem:execution-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class ExecutionPipelineTest {
    @Autowired private AnalysisTaskRepository tasks;
    @Autowired private AnalysisSnapshotRepository snapshots;
    @Autowired private TaskExecutionWorker worker;
    @Autowired private TaskOrchestrator orchestrator;
    @Autowired private TaskStateService state;
    @Autowired private AnalysisServiceImpl service;
    @Autowired private ExecutionStageRepository stages;
    @Autowired private ExecutionRunRepository runs;

    @Test
    void syntheticStatementProducesTraceableRunAndSnapshot() throws Exception {
        String taskId = "TEST_PIPELINE_01";
        tasks.save(AnalysisTask.builder().taskId(taskId).companyCode("DEMO01")
                .companyName("合成测试样本").reportPeriod("2024A")
                .analysisType(AnalysisTask.AnalysisType.FULL)
                .uploadedFilePath(new ClassPathResource("samples/demo-statement.txt").getFile().getAbsolutePath())
                .status(AnalysisTask.TaskStatus.CREATED).createdAt(LocalDateTime.now()).build());

        worker.runPipeline(taskId);

        assertThat(tasks.findById(taskId).orElseThrow().getStatus()).isEqualTo(AnalysisTask.TaskStatus.COMPLETED);
        assertThat(snapshots.findByTaskId(taskId)).isPresent();
        var runs = orchestrator.runs(taskId);
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).getInputSha256()).matches("[0-9a-f]{64}");
        assertThat(runs.get(0).getStatus()).isEqualTo("COMPLETED");
        assertThat(orchestrator.stages(runs.get(0).getId())).extracting("name")
                .containsExactly("EVIDENCE", "FINANCIAL", "ARTICULATION", "RISK", "VALUATION", "REPORT", "REVIEW");
        var storedReport = service.getReport(taskId);
        assertThat(storedReport.getReviewResult()).isNotNull();
        assertThat(storedReport.getReviewResult().getStatus()).isIn("ok", "degraded");
        assertThat(service.getMetrics(taskId).getCoreMetrics().getRevenue()).isNotNull();
        assertThat(service.getAnomalies(taskId)).isNotNull();
        assertThat(service.getValuation(taskId).getBusinessStatus()).isEqualTo("BLOCKED_BY_DATA_QUALITY");
    }

    @Test
    void cancellationPreventsLateSnapshotAndAllowsRerun() throws Exception {
        String id = "TEST_CANCEL_01";
        tasks.save(AnalysisTask.builder().taskId(id).companyCode("DEMO01")
                .companyName("Synthetic sample").reportPeriod("2024A")
                .analysisType(AnalysisTask.AnalysisType.FULL)
                .uploadedFilePath(new ClassPathResource("samples/demo-statement.txt").getFile().getAbsolutePath())
                .status(AnalysisTask.TaskStatus.CREATED).createdAt(LocalDateTime.now()).build());
        assertThat(state.claim(id)).isTrue();
        service.cancelTask(id);
        assertThat(state.progress(id, 90, "late progress")).isFalse();
        assertThat(state.complete(id, AnalysisSnapshot.builder().taskId(id).build(), "none", 0, 1)).isFalse();
        assertThat(tasks.findById(id).orElseThrow().getStatus()).isEqualTo(AnalysisTask.TaskStatus.CANCELLED);
        assertThat(snapshots.findByTaskId(id)).isEmpty();
        worker.runPipeline(id);
        assertThat(orchestrator.runs(id)).isEmpty();
        tasks.findById(id).ifPresent(task -> {
            task.setStatus(AnalysisTask.TaskStatus.QUEUED);
            tasks.save(task);
        });
        worker.runPipeline(id);
        assertThat(tasks.findById(id).orElseThrow().getStatus()).isEqualTo(AnalysisTask.TaskStatus.COMPLETED);
        assertThat(orchestrator.runs(id)).hasSize(1);
        service.deleteTask(id);
        assertThat(runs.findByTaskIdOrderByStartedAtDesc(id)).isEmpty();
        assertThat(snapshots.findByTaskId(id)).isEmpty();
    }

    @Test
    void restartClosesInterruptedStageAndTask() throws Exception {
        String id = "TEST_RESTART_01";
        String file = new ClassPathResource("samples/demo-statement.txt").getFile().getAbsolutePath();
        tasks.save(AnalysisTask.builder().taskId(id).companyCode("DEMO01")
                .companyName("Synthetic sample").reportPeriod("2024A")
                .analysisType(AnalysisTask.AnalysisType.FULL).uploadedFilePath(file)
                .status(AnalysisTask.TaskStatus.RUNNING).createdAt(LocalDateTime.now()).build());
        String runId = orchestrator.start(id, file);
        stages.save(ExecutionStage.builder().id("TEST_STAGE_RESTART").runId(runId).name("RISK")
                .status("RUNNING").startedAt(LocalDateTime.now()).build());
        orchestrator.recoverInterruptedRuns();
        assertThat(runs.findById(runId).orElseThrow().getErrorCode()).isEqualTo("PROCESS_RESTARTED");
        assertThat(stages.findByRunIdOrderByStartedAtAsc(runId)).extracting("status").containsExactly("FAILED");
        assertThat(tasks.findById(id).orElseThrow().getStatus()).isEqualTo(AnalysisTask.TaskStatus.FAILED);
    }
}

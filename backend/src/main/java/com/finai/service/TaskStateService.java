package com.finai.service;

import com.finai.model.AnalysisSnapshot;
import com.finai.model.AnalysisTask;
import com.finai.repository.AnalysisSnapshotRepository;
import com.finai.repository.AnalysisTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TaskStateService {
    private final AnalysisTaskRepository tasks;
    private final AnalysisSnapshotRepository snapshots;

    @Transactional
    public boolean claim(String taskId) {
        AnalysisTask task = tasks.findLocked(taskId).orElseThrow();
        if (task.getStatus() != AnalysisTask.TaskStatus.CREATED && task.getStatus() != AnalysisTask.TaskStatus.QUEUED) {
            return false;
        }
        task.setStatus(AnalysisTask.TaskStatus.RUNNING);
        task.setStartedAt(LocalDateTime.now());
        task.setProgress(10);
        task.setCurrentStep("准备解析");
        return true;
    }

    @Transactional
    public boolean progress(String taskId, int percent, String step) {
        return tasks.findLocked(taskId).map(task -> {
            if (task.getStatus() != AnalysisTask.TaskStatus.RUNNING) return false;
            task.setProgress(percent);
            task.setCurrentStep(step);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean complete(String taskId, AnalysisSnapshot snapshot, String llmModel, int llmCalls, long elapsedMs) {
        AnalysisTask task = tasks.findLocked(taskId).orElseThrow();
        if (task.getStatus() != AnalysisTask.TaskStatus.RUNNING) return false;
        snapshots.save(snapshot);
        task.setStatus(AnalysisTask.TaskStatus.COMPLETED);
        task.setErrorMessage(null);
        task.setProgress(100);
        task.setCurrentStep("完成");
        task.setCompletedAt(LocalDateTime.now());
        task.setExecutionTimeMs(elapsedMs);
        task.setLlmModel(llmModel);
        task.setLlmCallCount(llmCalls);
        return true;
    }

    @Transactional
    public boolean fail(String taskId, String code) {
        return tasks.findLocked(taskId).map(task -> {
            if (task.getStatus() != AnalysisTask.TaskStatus.RUNNING) return false;
            task.setStatus(AnalysisTask.TaskStatus.FAILED);
            task.setErrorMessage(code);
            task.setCompletedAt(LocalDateTime.now());
            return true;
        }).orElse(false);
    }
}

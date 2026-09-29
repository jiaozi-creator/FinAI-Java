package com.finai.repository;

import com.finai.model.ExecutionStage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExecutionStageRepository extends JpaRepository<ExecutionStage, String> {
    List<ExecutionStage> findByRunIdOrderByStartedAtAsc(String runId);
    void deleteByRunId(String runId);
}

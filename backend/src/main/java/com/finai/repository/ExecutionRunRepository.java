package com.finai.repository;

import com.finai.model.ExecutionRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExecutionRunRepository extends JpaRepository<ExecutionRun, String> {
    List<ExecutionRun> findByTaskIdOrderByStartedAtDesc(String taskId);
    List<ExecutionRun> findByStatus(String status);
    void deleteByTaskId(String taskId);
}

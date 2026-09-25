package com.finai.repository;

import com.finai.model.AnalysisSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AnalysisSnapshotRepository extends JpaRepository<AnalysisSnapshot, String> {

    Optional<AnalysisSnapshot> findByTaskId(String taskId);

    void deleteByTaskId(String taskId);
}

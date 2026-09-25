package com.finai.repository;

import com.finai.model.AnalysisTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 分析任务数据访问层
 */
@Repository
public interface AnalysisTaskRepository extends JpaRepository<AnalysisTask, String> {

    /**
     * 根据公司代码查询任务
     */
    List<AnalysisTask> findByCompanyCodeOrderByCreatedAtDesc(String companyCode);

    /**
     * 根据状态查询任务
     */
    List<AnalysisTask> findByStatusOrderByCreatedAtDesc(AnalysisTask.TaskStatus status);

    /**
     * 根据公司代码和状态查询任务
     */
    List<AnalysisTask> findByCompanyCodeAndStatusOrderByCreatedAtDesc(
            String companyCode, AnalysisTask.TaskStatus status);

    /**
     * 根据公司代码和状态分页查询
     */
    Page<AnalysisTask> findByCompanyCodeAndStatusOrderByCreatedAtDesc(
            String companyCode, AnalysisTask.TaskStatus status, Pageable pageable);

    /**
     * 分页查询任务
     */
    Page<AnalysisTask> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 根据公司代码分页查询
     */
    Page<AnalysisTask> findByCompanyCodeOrderByCreatedAtDesc(String companyCode, Pageable pageable);

    /**
     * 根据状态分页查询
     */
    Page<AnalysisTask> findByStatusOrderByCreatedAtDesc(
            AnalysisTask.TaskStatus status, Pageable pageable);

    /**
     * 查询指定时间范围内的任务
     */
    @Query("SELECT t FROM AnalysisTask t WHERE t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
    List<AnalysisTask> findTasksInDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * 统计各状态的任务数量
     */
    long countByStatus(AnalysisTask.TaskStatus status);

    /**
     * 查询运行中的任务
     */
    @Query("SELECT t FROM AnalysisTask t WHERE t.status IN ('RUNNING', 'QUEUED') ORDER BY t.createdAt")
    List<AnalysisTask> findRunningTasks();

    /**
     * 查询指定公司最近的任务
     */
    Optional<AnalysisTask> findFirstByCompanyCodeOrderByCreatedAtDesc(String companyCode);
}

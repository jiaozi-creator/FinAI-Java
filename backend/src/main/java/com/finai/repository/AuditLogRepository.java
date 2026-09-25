package com.finai.repository;

import com.finai.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志数据访问层
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * 根据任务ID查询日志
     */
    List<AuditLog> findByTaskIdOrderByCreatedAtAsc(String taskId);

    /**
     * 根据日志类型查询
     */
    List<AuditLog> findByLogTypeOrderByCreatedAtDesc(AuditLog.LogType logType);

    /**
     * 根据任务ID和日志类型查询
     */
    List<AuditLog> findByTaskIdAndLogTypeOrderByCreatedAtAsc(
            String taskId, AuditLog.LogType logType);

    /**
     * 分页查询日志
     */
    Page<AuditLog> findByTaskIdOrderByCreatedAtDesc(String taskId, Pageable pageable);

    /**
     * 查询指定时间范围内的日志
     */
    @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN :startDate AND :endDate ORDER BY a.createdAt DESC")
    List<AuditLog> findLogsInDateRange(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * 查询错误日志
     */
    @Query("SELECT a FROM AuditLog a WHERE a.status = 'ERROR' OR a.logType = 'ERROR' ORDER BY a.createdAt DESC")
    List<AuditLog> findErrorLogs();

    /**
     * 查询LLM调用日志
     */
    List<AuditLog> findByLogTypeAndTaskIdOrderByCreatedAtAsc(
            AuditLog.LogType logType, String taskId);

    /**
     * 统计任务的日志数量
     */
    long countByTaskId(String taskId);

    /**
     * 删除任务的所有日志
     */
    void deleteByTaskId(String taskId);

    /**
     * 删除指定时间之前的日志
     */
    void deleteByCreatedAtBefore(LocalDateTime date);
}

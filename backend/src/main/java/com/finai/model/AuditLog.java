package com.finai.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计日志实体
 *
 * 记录系统的所有重要操作，支持回放和复现
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_log", indexes = {
    @Index(name = "idx_task_id", columnList = "task_id"),
    @Index(name = "idx_log_type", columnList = "log_type"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的任务ID
     */
    @Column(name = "task_id", length = 50)
    private String taskId;

    /**
     * 日志类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "log_type", nullable = false)
    private LogType logType;

    /**
     * 操作名称
     */
    @Column(name = "operation", nullable = false, length = 100)
    private String operation;

    /**
     * 详细信息
     */
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    /**
     * 输入参数 (JSON)
     */
    @Column(name = "input_params", columnDefinition = "TEXT")
    private String inputParams;

    /**
     * 输出结果 (JSON)
     */
    @Column(name = "output_result", columnDefinition = "TEXT")
    private String outputResult;

    /**
     * 执行状态
     */
    @Column(name = "status", length = 20)
    private String status;

    /**
     * 错误信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 执行时长(毫秒)
     */
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    /**
     * 工具或模型名。长哈希放 details，不放这个长度有限的字段。
     */
    @Column(name = "version", length = 120)
    private String version;

    /**
     * 用户标识
     */
    @Column(name = "user_id", length = 50)
    private String userId;

    /**
     * IP地址
     */
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 日志类型枚举
     */
    public enum LogType {
        API_CALL,           // API调用
        TASK_EXECUTION,     // 任务执行
        TOOL_INVOCATION,    // 工具调用
        LLM_REQUEST,        // LLM请求
        DATA_PARSING,       // 数据解析
        CALCULATION,        // 计算操作
        VALIDATION,         // 验证操作
        REPORT_GENERATION,  // 报告生成
        ERROR,              // 错误
        SYSTEM              // 系统事件
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

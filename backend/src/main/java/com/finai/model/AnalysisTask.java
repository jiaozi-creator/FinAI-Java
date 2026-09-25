package com.finai.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 分析任务实体
 *
 * 记录每次分析任务的执行状态和结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "analysis_task")
public class AnalysisTask {

    @Id
    @Column(name = "task_id", length = 50)
    private String taskId;

    /**
     * 公司代码
     */
    @Column(name = "company_code", nullable = false, length = 20)
    private String companyCode;

    /**
     * 公司名称
     */
    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    /**
     * 报告期
     */
    @Column(name = "report_period", nullable = false, length = 20)
    private String reportPeriod;

    /**
     * 分析类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false)
    private AnalysisType analysisType;

    /**
     * 任务状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TaskStatus status;

    /**
     * 当前步骤
     */
    @Column(name = "current_step", length = 50)
    private String currentStep;

    /**
     * 进度 (0-100)
     */
    @Column(name = "progress")
    private Integer progress;

    /**
     * 上传的文件路径
     */
    @Column(name = "uploaded_file_path", length = 500)
    private String uploadedFilePath;

    /**
     * 报告文件路径
     */
    @Column(name = "report_file_path", length = 500)
    private String reportFilePath;

    /**
     * 错误信息
     */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /**
     * 执行时长(毫秒)
     */
    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    /**
     * 使用的LLM模型
     */
    @Column(name = "llm_model", length = 100)
    private String llmModel;

    /**
     * LLM调用次数
     */
    @Column(name = "llm_call_count")
    private Integer llmCallCount;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 开始时间
     */
    @Column(name = "started_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    @Column(name = "completed_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    /**
     * 分析类型枚举
     */
    public enum AnalysisType {
        QUICK,      // 快速分析
        STANDARD,   // 标准分析
        FULL,       // 完整分析(含估值)
        VALUATION_ONLY  // 仅估值
    }

    /**
     * 任务状态枚举
     */
    public enum TaskStatus {
        CREATED,    // 已创建
        QUEUED,     // 排队中
        RUNNING,    // 运行中
        COMPLETED,  // 已完成
        FAILED,     // 失败
        CANCELLED   // 已取消
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = TaskStatus.CREATED;
        }
        if (progress == null) {
            progress = 0;
        }
    }
}

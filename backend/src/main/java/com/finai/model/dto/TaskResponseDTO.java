package com.finai.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.finai.model.AnalysisTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponseDTO {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 公司代码
     */
    private String companyCode;

    /**
     * 公司名称
     */
    private String companyName;

    /**
     * 报告期
     */
    private String reportPeriod;

    /**
     * 分析类型
     */
    private AnalysisTask.AnalysisType analysisType;

    /**
     * 任务状态
     */
    private AnalysisTask.TaskStatus status;

    /**
     * 当前步骤
     */
    private String currentStep;

    /**
     * 进度(0-100)
     */
    private Integer progress;

    /**
     * 报告路径
     */
    private String reportPath;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 执行时长(毫秒)
     */
    private Long executionTimeMs;

    /**
     * LLM模型
     */
    private String llmModel;

    /**
     * LLM调用次数
     */
    private Integer llmCallCount;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime completedAt;

    /**
     * 从实体转换
     */
    public static TaskResponseDTO fromEntity(AnalysisTask task) {
        if (task == null) {
            return null;
        }
        return TaskResponseDTO.builder()
                .taskId(task.getTaskId())
                .companyCode(task.getCompanyCode())
                .companyName(task.getCompanyName())
                .reportPeriod(task.getReportPeriod())
                .analysisType(task.getAnalysisType())
                .status(task.getStatus())
                .currentStep(task.getCurrentStep())
                .progress(task.getProgress())
                .reportPath(task.getReportFilePath())
                .errorMessage(task.getErrorMessage())
                .executionTimeMs(task.getExecutionTimeMs())
                .llmModel(task.getLlmModel())
                .llmCallCount(task.getLlmCallCount())
                .createdAt(task.getCreatedAt())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .build();
    }
}

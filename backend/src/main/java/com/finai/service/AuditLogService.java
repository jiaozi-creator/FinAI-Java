package com.finai.service;

import com.finai.model.AuditLog;
import com.finai.model.dto.TaskRequestDTO;

import java.util.List;

/**
 * 审计日志服务接口
 */
public interface AuditLogService {

    /**
     * 记录任务创建
     */
    void logTaskCreation(String taskId, TaskRequestDTO request);

    /**
     * 记录任务完成
     */
    void logTaskCompletion(String taskId);

    /**
     * 记录任务失败
     */
    void logTaskFailure(String taskId, Exception e);

    /**
     * 记录任务取消
     */
    void logTaskCancellation(String taskId);

    /**
     * 记录任务删除
     */
    void logTaskDeletion(String taskId);

    /**
     * 记录工具调用。input、output 原样落库，不截断。
     */
    void logToolInvocation(String taskId, String toolName, String input, String output, Long executionTime);

    /**
     * 记录打开过的文件和内容哈希。
     */
    void logFileAccess(String taskId, String path, String sha256, long bytes, int pages);

    /**
     * 记录 LLM 请求。prompt 和 response 原样落库。
     * details 放模型、温度、提示词版本、是否离开本机。
     */
    void logLLMRequest(String taskId, String details, String model, String prompt, String response, Long executionTime);

    /**
     * 记录错误
     */
    void logError(String taskId, String operation, String errorMessage);

    /**
     * 获取任务的审计日志
     */
    List<AuditLog> getTaskLogs(String taskId);

    /**
     * 导出任务日志
     */
    String exportTaskLogs(String taskId);
}

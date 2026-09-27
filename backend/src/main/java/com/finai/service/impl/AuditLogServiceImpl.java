package com.finai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finai.model.AuditLog;
import com.finai.model.dto.TaskRequestDTO;
import com.finai.repository.AuditLogRepository;
import com.finai.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 审计日志服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void logTaskCreation(String taskId, TaskRequestDTO request) {
        try {
            String inputParams = objectMapper.writeValueAsString(request);
            AuditLog auditLog = AuditLog.builder()
                    .taskId(taskId)
                    .logType(AuditLog.LogType.TASK_EXECUTION)
                    .operation("CREATE_TASK")
                    .details("Task created")
                    .inputParams(inputParams)
                    .status("SUCCESS")
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log task creation", e);
        }
    }

    @Override
    @Transactional
    public void logTaskCompletion(String taskId) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .taskId(taskId)
                    .logType(AuditLog.LogType.TASK_EXECUTION)
                    .operation("COMPLETE_TASK")
                    .details("Task completed successfully")
                    .status("SUCCESS")
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log task completion", e);
        }
    }

    @Override
    @Transactional
    public void logTaskFailure(String taskId, Exception exception) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .taskId(taskId)
                    .logType(AuditLog.LogType.ERROR)
                    .operation("TASK_EXECUTION")
                    .details("Task execution failed")
                    .status("ERROR")
                    .errorMessage(exception.getMessage())
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log task failure", e);
        }
    }

    @Override
    @Transactional
    public void logTaskCancellation(String taskId) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .taskId(taskId)
                    .logType(AuditLog.LogType.TASK_EXECUTION)
                    .operation("CANCEL_TASK")
                    .details("Task cancelled by user")
                    .status("SUCCESS")
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log task cancellation", e);
        }
    }

    @Override
    @Transactional
    public void logTaskDeletion(String taskId) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .taskId(taskId)
                    .logType(AuditLog.LogType.SYSTEM)
                    .operation("DELETE_TASK")
                    .details("Task and related data deleted")
                    .status("SUCCESS")
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log task deletion", e);
        }
    }

    @Override
    @Transactional
    public void logToolInvocation(String taskId, String toolName, String input, String output, Long executionTime) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .taskId(taskId)
                    .logType(AuditLog.LogType.TOOL_INVOCATION)
                    .operation(toolName)
                    .details("Tool invoked")
                    .inputParams(input)
                    .outputResult(output)
                    .status("SUCCESS")
                    .executionTimeMs(executionTime)
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log tool invocation", e);
        }
    }

    @Override
    @Transactional
    public void logFileAccess(String taskId, String path, String sha256, long bytes, int pages) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .taskId(taskId)
                    .logType(AuditLog.LogType.DATA_PARSING)
                    .operation("file.access")
                    .details("sha256=" + sha256 + ",bytes=" + bytes + ",pages=" + pages)
                    .inputParams(path)
                    .outputResult(sha256)
                    .status("SUCCESS")
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log file access", e);
        }
    }

    @Override
    @Transactional
    public void logLLMRequest(String taskId, String details, String model, String prompt, String response, Long executionTime) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .taskId(taskId)
                    .logType(AuditLog.LogType.LLM_REQUEST)
                    .operation("LLM_CALL")
                    .details(details)
                    .inputParams(prompt)
                    .outputResult(response)
                    .status("SUCCESS")
                    .executionTimeMs(executionTime)
                    .version(model)
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log LLM request", e);
        }
    }

    @Override
    @Transactional
    public void logError(String taskId, String operation, String errorMessage) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .taskId(taskId)
                    .logType(AuditLog.LogType.ERROR)
                    .operation(operation)
                    .details("Error occurred")
                    .status("ERROR")
                    .errorMessage(errorMessage)
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log error", e);
        }
    }

    @Override
    public List<AuditLog> getTaskLogs(String taskId) {
        return auditLogRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    @Override
    public String exportTaskLogs(String taskId) {
        List<AuditLog> logs = getTaskLogs(taskId);
        StringBuilder sb = new StringBuilder();

        sb.append("=".repeat(80)).append("\n");
        sb.append("Audit Log Export for Task: ").append(taskId).append("\n");
        sb.append("Generated at: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        sb.append("=".repeat(80)).append("\n\n");

        for (AuditLog log : logs) {
            sb.append("[").append(log.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("] ");
            sb.append("[").append(log.getLogType()).append("] ");
            sb.append(log.getOperation()).append("\n");
            sb.append("  Status: ").append(log.getStatus()).append("\n");
            sb.append("  Details: ").append(log.getDetails()).append("\n");

            if (log.getExecutionTimeMs() != null) {
                sb.append("  Execution Time: ").append(log.getExecutionTimeMs()).append("ms\n");
            }

            if (log.getErrorMessage() != null) {
                sb.append("  Error: ").append(log.getErrorMessage()).append("\n");
            }

            sb.append("\n");
        }

        return sb.toString();
    }
}

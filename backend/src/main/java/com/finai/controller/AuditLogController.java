package com.finai.controller;

import com.finai.model.AuditLog;
import com.finai.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/tasks/{taskId}/logs")
    public List<Map<String, Object>> logs(@PathVariable String taskId) {
        return auditLogService.getTaskLogs(taskId).stream().map(this::view).toList();
    }

    private Map<String, Object> view(AuditLog log) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("operation", log.getOperation());
        row.put("logType", log.getLogType() == null ? null : log.getLogType().name());
        row.put("status", log.getStatus());
        row.put("details", clip(log.getDetails()));
        row.put("output", clip(log.getOutputResult()));
        row.put("executionTimeMs", log.getExecutionTimeMs());
        row.put("createdAt", log.getCreatedAt());
        return row;
    }

    private static String clip(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 240 ? value : value.substring(0, 240);
    }
}

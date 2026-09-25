package com.finai.controller;

import com.finai.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 只读工具。不写任务，不补数字。
 */
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpToolController {

    private final AnalysisService analysisService;

    @GetMapping("/tools")
    public List<Map<String, String>> tools() {
        return List.of(
                tool("statement.metrics", "返回已计算的财务指标，缺的字段是 null"),
                tool("statement.evidence", "返回科目证据：页码、单位、原文片段"),
                tool("valuation.dcf", "返回现金流折现区间和假设，没有区间就是未计算")
        );
    }

    @PostMapping("/tools/{name}")
    public Object call(@PathVariable String name, @RequestBody Map<String, String> body) {
        String taskId = body.get("taskId");
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId 必填");
        }
        return switch (name) {
            case "statement.metrics" -> analysisService.getMetrics(taskId);
            case "statement.evidence" -> analysisService.getEvidence(taskId, body.get("fieldId"));
            case "valuation.dcf" -> analysisService.getValuation(taskId);
            default -> throw new IllegalArgumentException("未知工具: " + name);
        };
    }

    private static Map<String, String> tool(String name, String description) {
        return Map.of("name", name, "description", description);
    }
}

package com.finai.agent;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/** Structured result. Agents may explain results but cannot manufacture facts. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResult {
    private String status;
    @Builder.Default private List<Map<String, Object>> facts = List.of();
    @Builder.Default private List<Map<String, Object>> inferences = List.of();
    @Builder.Default private List<Map<String, Object>> assumptions = List.of();
    @Builder.Default private List<String> evidenceRefs = List.of();
    @Builder.Default private List<String> warnings = List.of();
    private String nextAction;
    @Builder.Default private List<Map<String, Object>> auditEvents = List.of();
}

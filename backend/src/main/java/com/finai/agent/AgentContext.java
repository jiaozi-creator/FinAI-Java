package com.finai.agent;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable-at-boundary snapshot passed between agents. */
@Data
@Builder
public class AgentContext {
    private AgentRequest request;
    @Builder.Default private Map<String, Object> values = new LinkedHashMap<>();

    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}

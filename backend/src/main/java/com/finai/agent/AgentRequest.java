package com.finai.agent;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Versioned, auditable input envelope shared by deterministic agents. */
@Data
@Builder
public class AgentRequest {
    private String taskId;
    private String action;
    private String inputSnapshotVersion;
    private List<String> allowedTools;
    private String traceId;
}

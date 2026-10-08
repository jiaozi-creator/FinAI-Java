package com.finai.agent;

import org.springframework.stereotype.Component;

@Component
public class EvidenceAgent implements Agent {
    public String name() { return "evidence"; }
    public AgentResult run(AgentContext context) {
        return AgentResult.builder().status(context == null ? "failed" : "ok")
                .nextAction("financial").build();
    }
}

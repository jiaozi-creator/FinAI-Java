package com.finai.agent;

import org.springframework.stereotype.Component;

@Component
public class ReportAgent implements Agent {
    public String name() { return "report"; }
    public AgentResult run(AgentContext context) {
        return AgentResult.builder().status(context == null ? "failed" : "ok")
                .nextAction("review").build();
    }
}

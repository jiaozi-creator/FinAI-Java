package com.finai.agent;

import org.springframework.stereotype.Component;

@Component
public class RiskAgent implements Agent {
    public String name() { return "risk"; }
    public AgentResult run(AgentContext context) {
        return AgentResult.builder().status(context == null ? "failed" : "ok")
                .nextAction("valuation").build();
    }
}

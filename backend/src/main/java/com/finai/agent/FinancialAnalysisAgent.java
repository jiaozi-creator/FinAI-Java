package com.finai.agent;

import org.springframework.stereotype.Component;

@Component
public class FinancialAnalysisAgent implements Agent {
    public String name() { return "financial"; }
    public AgentResult run(AgentContext context) {
        return AgentResult.builder().status(context == null ? "failed" : "ok")
                .nextAction("risk").build();
    }
}

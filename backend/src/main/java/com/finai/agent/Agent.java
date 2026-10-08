package com.finai.agent;

/** A deterministic stage in the fixed analysis pipeline. */
public interface Agent {
    String name();
    AgentResult run(AgentContext context);
}

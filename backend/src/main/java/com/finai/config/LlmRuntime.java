package com.finai.config;

import lombok.Getter;

@Getter
public class LlmRuntime {

    private final String provider;
    private final String model;
    private final double temperature;
    private final boolean mock;

    public LlmRuntime(String provider, String model, double temperature, boolean mock) {
        this.provider = provider;
        this.model = model;
        this.temperature = temperature;
        this.mock = mock;
    }

    public boolean leavesMachine() {
        return !mock;
    }
}

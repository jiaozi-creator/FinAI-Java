package com.finai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 配置
 */
@Slf4j
@Configuration
public class LangChain4jConfig {

    @Value("${finai.llm.provider:aliyun}")
    private String llmProvider;

    @Value("${finai.llm.model:qwen-plus}")
    private String modelName;

    @Value("${finai.llm.temperature:0.0}")
    private double temperature;

    @Value("${finai.llm.anthropic.api-key:}")
    private String anthropicApiKey;

    @Value("${finai.llm.openai.api-key:}")
    private String openaiApiKey;

    @Value("${finai.llm.aliyun.api-key:}")
    private String aliyunApiKey;

    @Value("${finai.llm.aliyun.base-url:}")
    private String aliyunBaseUrl;

    @Bean
    public LlmRuntime llmRuntime() {
        boolean mock = switch (llmProvider.toLowerCase()) {
            case "aliyun" -> aliyunApiKey.isBlank();
            case "claude" -> anthropicApiKey.isBlank();
            case "openai" -> openaiApiKey.isBlank();
            default -> true;
        };
        log.info("LLM runtime provider={} model={} temperature={} mock={}", llmProvider, modelName, temperature, mock);
        return new LlmRuntime(llmProvider, modelName, temperature, mock);
    }

    @Bean
    public ChatLanguageModel chatLanguageModel(LlmRuntime runtime) {
        if (runtime.isMock()) {
            log.warn("No API key for provider {}, using mock model", runtime.getProvider());
            return createMockModel();
        }
        if ("aliyun".equalsIgnoreCase(runtime.getProvider())) {
            return OpenAiChatModel.builder()
                    .apiKey(aliyunApiKey)
                    .baseUrl(aliyunBaseUrl.isEmpty() ? "https://dashscope.aliyuncs.com/compatible-mode/v1" : aliyunBaseUrl)
                    .modelName(runtime.getModel())
                    .temperature(runtime.getTemperature())
                    .maxTokens(2000)
                    .build();
        }
        if ("claude".equalsIgnoreCase(runtime.getProvider())) {
            return AnthropicChatModel.builder()
                    .apiKey(anthropicApiKey)
                    .modelName(runtime.getModel())
                    .temperature(runtime.getTemperature())
                    .maxTokens(2000)
                    .build();
        }
        if ("openai".equalsIgnoreCase(runtime.getProvider())) {
            return OpenAiChatModel.builder()
                    .apiKey(openaiApiKey)
                    .modelName(runtime.getModel())
                    .temperature(runtime.getTemperature())
                    .maxTokens(2000)
                    .build();
        }
        return createMockModel();
    }

    /**
     * 创建模拟模型（用于开发和测试）
     */
    private ChatLanguageModel createMockModel() {
        return messages -> {
            log.info("Using mock ChatLanguageModel (no API key configured)");
            String mockResponse = "这是一个模拟的AI回复。请配置 ANTHROPIC_API_KEY 或 OPENAI_API_KEY 环境变量以使用真实的AI模型。";
            return new dev.langchain4j.model.output.Response<>(
                    dev.langchain4j.data.message.AiMessage.from(mockResponse)
            );
        };
    }
}

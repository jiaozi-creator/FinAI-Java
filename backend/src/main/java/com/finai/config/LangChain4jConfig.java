package com.finai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

/**
 * LangChain4j 配置
 */
@Slf4j
@Configuration
public class LangChain4jConfig {

    @Value("${finai.llm.provider:aliyun}")
    private String llmProvider;

    @Value("${finai.llm.anthropic.api-key:}")
    private String anthropicApiKey;

    @Value("${finai.llm.openai.api-key:}")
    private String openaiApiKey;

    @Value("${finai.llm.aliyun.api-key:}")
    private String aliyunApiKey;

    @Value("${finai.llm.aliyun.base-url:}")
    private String aliyunBaseUrl;

    @Value("${finai.llm.model:qwen-plus}")
    private String model;

    @Value("${finai.llm.temperature:0.0}")
    private double temperature;

    @Value("${finai.llm.max-tokens:4096}")
    private int maxTokens;

    @Value("${finai.llm.timeout:60000}")
    private long timeoutMs;

    @Value("${finai.dev.mock-llm:false}")
    private boolean mockEnabled;

    @Value("${finai.competition-mode:true}")
    private boolean competitionMode;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("Initializing ChatLanguageModel with provider: {}", llmProvider);

        if ("mock".equalsIgnoreCase(llmProvider)) {
            if (!mockEnabled) {
                throw new IllegalStateException("Mock model requires DEV_MOCK_LLM=true");
            }
            return createMockModel();
        }
        if (competitionMode && !"aliyun".equalsIgnoreCase(llmProvider)) {
            throw new IllegalStateException("Competition mode only permits the Aliyun provider");
        }
        if (maxTokens <= 0 || timeoutMs <= 0 || temperature < 0 || temperature > 2) {
            throw new IllegalStateException("Invalid model token, timeout or temperature configuration");
        }

        if ("aliyun".equalsIgnoreCase(llmProvider)) {
            requireKey(aliyunApiKey, "ALIYUN_API_KEY");
            if (competitionMode && (!model.toLowerCase(java.util.Locale.ROOT).startsWith("qwen-")
                    || (!aliyunBaseUrl.isBlank() && !"https://dashscope.aliyuncs.com/compatible-mode/v1".equals(aliyunBaseUrl)))) {
                throw new IllegalStateException("Competition mode requires a Qwen model on the official Aliyun endpoint");
            }

            log.info("Using Aliyun Qwen model");
            // Use OpenAI-compatible API for Aliyun
            return OpenAiChatModel.builder()
                    .apiKey(aliyunApiKey)
                    .baseUrl(aliyunBaseUrl.isEmpty() ? "https://dashscope.aliyuncs.com/compatible-mode/v1" : aliyunBaseUrl)
                    .modelName(model)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .build();
        } else if ("claude".equalsIgnoreCase(llmProvider)) {
            requireKey(anthropicApiKey, "ANTHROPIC_API_KEY");

            log.info("Using Claude (Anthropic) model");
            return AnthropicChatModel.builder()
                    .apiKey(anthropicApiKey)
                    .modelName(model)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .build();
        } else if ("openai".equalsIgnoreCase(llmProvider)) {
            requireKey(openaiApiKey, "OPENAI_API_KEY");

            log.info("Using OpenAI GPT model");
            return OpenAiChatModel.builder()
                    .apiKey(openaiApiKey)
                    .modelName(model)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .build();
        } else {
            throw new IllegalStateException("Unknown LLM provider: " + llmProvider);
        }
    }

    private void requireKey(String key, String variable) {
        if (key == null || key.isBlank() || key.startsWith("your_")) {
            throw new IllegalStateException("Missing model API key: " + variable);
        }
    }

    /**
     * 创建模拟模型（用于开发和测试）
     */
    private ChatLanguageModel createMockModel() {
        return messages -> {
            log.info("Using mock ChatLanguageModel (no API key configured)");
            String mockResponse = "这是一个模拟的AI回复。仅供演示，未调用真实模型。";
            return new dev.langchain4j.model.output.Response<>(
                    dev.langchain4j.data.message.AiMessage.from(mockResponse)
            );
        };
    }
}

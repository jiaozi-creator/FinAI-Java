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

    @Value("${finai.llm.anthropic.api-key:}")
    private String anthropicApiKey;

    @Value("${finai.llm.openai.api-key:}")
    private String openaiApiKey;

    @Value("${finai.llm.aliyun.api-key:}")
    private String aliyunApiKey;

    @Value("${finai.llm.aliyun.base-url:}")
    private String aliyunBaseUrl;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        log.info("Initializing ChatLanguageModel with provider: {}", llmProvider);

        if ("aliyun".equalsIgnoreCase(llmProvider)) {
            if (aliyunApiKey.isEmpty()) {
                log.warn("Aliyun API key not configured, using mock model");
                return createMockModel();
            }

            log.info("Using Aliyun Qwen model");
            // Use OpenAI-compatible API for Aliyun
            return OpenAiChatModel.builder()
                    .apiKey(aliyunApiKey)
                    .baseUrl(aliyunBaseUrl.isEmpty() ? "https://dashscope.aliyuncs.com/compatible-mode/v1" : aliyunBaseUrl)
                    .modelName("qwen-plus")  // 使用千问模型
                    .temperature(0.7)
                    .maxTokens(2000)
                    .build();
        } else if ("claude".equalsIgnoreCase(llmProvider)) {
            if (anthropicApiKey.isEmpty()) {
                log.warn("Anthropic API key not configured, using mock model");
                return createMockModel();
            }

            log.info("Using Claude (Anthropic) model");
            return AnthropicChatModel.builder()
                    .apiKey(anthropicApiKey)
                    .modelName("claude-3-sonnet-20240229")
                    .temperature(0.7)
                    .maxTokens(2000)
                    .build();
        } else if ("openai".equalsIgnoreCase(llmProvider)) {
            if (openaiApiKey.isEmpty()) {
                log.warn("OpenAI API key not configured, using mock model");
                return createMockModel();
            }

            log.info("Using OpenAI GPT model");
            return OpenAiChatModel.builder()
                    .apiKey(openaiApiKey)
                    .modelName("gpt-4")
                    .temperature(0.7)
                    .maxTokens(2000)
                    .build();
        } else {
            log.warn("Unknown LLM provider: {}, using mock model", llmProvider);
            return createMockModel();
        }
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

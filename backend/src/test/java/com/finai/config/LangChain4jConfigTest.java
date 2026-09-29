package com.finai.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LangChain4jConfigTest {
    private LangChain4jConfig config(String provider) {
        LangChain4jConfig config = new LangChain4jConfig();
        ReflectionTestUtils.setField(config, "llmProvider", provider);
        ReflectionTestUtils.setField(config, "model", "qwen-plus");
        ReflectionTestUtils.setField(config, "aliyunBaseUrl", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        ReflectionTestUtils.setField(config, "aliyunApiKey", "test-key");
        ReflectionTestUtils.setField(config, "maxTokens", 100);
        ReflectionTestUtils.setField(config, "timeoutMs", 1000L);
        ReflectionTestUtils.setField(config, "temperature", 0.0);
        ReflectionTestUtils.setField(config, "competitionMode", true);
        return config;
    }

    @Test
    void requiresKeyInsteadOfFallingBackToMock() {
        LangChain4jConfig config = config("aliyun");
        ReflectionTestUtils.setField(config, "aliyunApiKey", "");
        assertThatThrownBy(config::chatLanguageModel).hasMessageContaining("ALIYUN_API_KEY");
    }

    @Test
    void mockRequiresExplicitFlag() {
        LangChain4jConfig config = config("mock");
        assertThatThrownBy(config::chatLanguageModel).hasMessageContaining("DEV_MOCK_LLM");
        ReflectionTestUtils.setField(config, "mockEnabled", true);
        assertThat(config.chatLanguageModel().generate("test")).contains("模拟");
    }

    @Test
    void rejectsNonDomesticAndUnrecognizedProviders() {
        assertThatThrownBy(config("claude")::chatLanguageModel).hasMessageContaining("Competition mode");
        LangChain4jConfig unknown = config("invalid");
        ReflectionTestUtils.setField(unknown, "competitionMode", false);
        assertThatThrownBy(unknown::chatLanguageModel).hasMessageContaining("Unknown LLM provider");
    }

    @Test
    void acceptsOfficialAliyunAndRejectsModelProxyInCompetition() {
        LangChain4jConfig config = config("aliyun");
        assertThat(config.chatLanguageModel()).isNotNull();
        ReflectionTestUtils.setField(config, "aliyunBaseUrl", "https://example.com/v1");
        assertThatThrownBy(config::chatLanguageModel).hasMessageContaining("official Aliyun endpoint");
    }
}

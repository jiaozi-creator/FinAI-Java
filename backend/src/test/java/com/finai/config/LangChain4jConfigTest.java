package com.finai.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LangChain4jConfigTest {
    private LangChain4jConfig config(String provider) {
        LangChain4jConfig config = new LangChain4jConfig();
        ReflectionTestUtils.setField(config, "llmProvider", provider);
        ReflectionTestUtils.setField(config, "model", "qwen3.7-flash");
        ReflectionTestUtils.setField(config, "aliyunBaseUrl", "https://dashscope.aliyuncs.com/compatible-mode/v1");
        ReflectionTestUtils.setField(config, "aliyunApiKey", "test-key");
        ReflectionTestUtils.setField(config, "maxTokens", 100);
        ReflectionTestUtils.setField(config, "timeoutMs", 1000L);
        ReflectionTestUtils.setField(config, "temperature", 0.0);
        ReflectionTestUtils.setField(config, "competitionMode", true);
        return config;
    }

    @Test
    void exposesRuntimeMatchingProvider() {
        LlmRuntime runtime = config("aliyun").llmRuntime();
        assertThat(runtime.isMock()).isFalse();
        assertThat(runtime.leavesMachine()).isTrue();
        assertThat(runtime.getModel()).isEqualTo("qwen3.7-flash");

        LangChain4jConfig mock = config("mock");
        ReflectionTestUtils.setField(mock, "mockEnabled", true);
        assertThat(mock.llmRuntime().isMock()).isTrue();
        assertThat(mock.llmRuntime().leavesMachine()).isFalse();
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

    @Test
    void acceptsAliyunQwen3NamingAndRejectsMultimodal() {
        assertThat(LangChain4jConfig.isAllowedCompetitionQwenModel("qwen3.7-flash")).isTrue();
        assertThat(LangChain4jConfig.isAllowedCompetitionQwenModel("qwen3.8-max")).isTrue();
        assertThat(LangChain4jConfig.isAllowedCompetitionQwenModel("qwen-plus")).isTrue();
        assertThat(LangChain4jConfig.isAllowedCompetitionQwenModel("qwen3.8-omni-flash")).isFalse();
        assertThat(LangChain4jConfig.isAllowedCompetitionQwenModel("claude-3-sonnet-20240229")).isFalse();

        LangChain4jConfig config = config("aliyun");
        ReflectionTestUtils.setField(config, "model", "qwen3.8-omni-flash");
        assertThatThrownBy(config::chatLanguageModel).hasMessageContaining("allowedQwen=false");
    }
}

package com.finai.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DotenvLoaderTest {

    @TempDir
    Path dir;

    @Test
    void parsesCommentsQuotesExportAndBom() {
        Map<String, String> entries = DotenvLoader.parse("""
                \uFEFF# comment
                export LLM_PROVIDER=aliyun
                ALIYUN_API_KEY="sk-test"
                EMPTY=

                LLM_MODEL='qwen3.7-flash'
                """);

        assertThat(entries)
                .containsEntry("LLM_PROVIDER", "aliyun")
                .containsEntry("ALIYUN_API_KEY", "sk-test")
                .containsEntry("EMPTY", "")
                .containsEntry("LLM_MODEL", "qwen3.7-flash");
    }

    @Test
    void rejectsMalformedLineWithoutEchoingIt() {
        assertThatThrownBy(() -> DotenvLoader.parse("not a pair\n"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("line 1")
                .hasMessageNotContaining("not a pair");
    }

    @Test
    void doesNotOverrideExistingProperty() throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, "DOTENV_LOADER_TEST_KEY=from-file\n");
        System.setProperty("DOTENV_LOADER_TEST_KEY", "from-jvm");
        try {
            assertThat(DotenvLoader.apply(env)).isZero();
            assertThat(System.getProperty("DOTENV_LOADER_TEST_KEY")).isEqualTo("from-jvm");
        } finally {
            System.clearProperty("DOTENV_LOADER_TEST_KEY");
        }
    }

    @Test
    void loadsMissingPropertyFromFile() throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, "DOTENV_LOADER_TEST_KEY=from-file\n");
        System.clearProperty("DOTENV_LOADER_TEST_KEY");
        try {
            assertThat(DotenvLoader.apply(env)).isEqualTo(1);
            assertThat(System.getProperty("DOTENV_LOADER_TEST_KEY")).isEqualTo("from-file");
        } finally {
            System.clearProperty("DOTENV_LOADER_TEST_KEY");
        }
    }
}

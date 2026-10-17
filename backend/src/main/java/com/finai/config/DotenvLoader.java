package com.finai.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把当前目录和上一级目录的 .env 填进系统属性。
 * Spring 的 ${ALIYUN_API_KEY:} 只认进程环境和系统属性，mvn spring-boot:run 不会自己读 .env。
 * 已经存在的非空环境变量或系统属性不覆盖。
 */
public final class DotenvLoader {

    private DotenvLoader() {
    }

    public static int load() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        int loaded = apply(cwd.resolve(".env"));
        loaded += apply(cwd.getParent() == null ? null : cwd.getParent().resolve(".env"));
        return loaded;
    }

    static int apply(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return 0;
        }
        Map<String, String> entries;
        try {
            entries = parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read " + file.toAbsolutePath().normalize(), ex);
        }
        int loaded = 0;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (alreadySet(entry.getKey()) || entry.getValue().isBlank()) {
                continue;
            }
            System.setProperty(entry.getKey(), entry.getValue());
            loaded++;
        }
        if (loaded > 0) {
            System.out.println("Loaded " + loaded + " entries from " + file.toAbsolutePath().normalize());
        }
        return loaded;
    }

    static Map<String, String> parse(String content) {
        String text = content.startsWith("\uFEFF") ? content.substring(1) : content;
        Map<String, String> entries = new LinkedHashMap<>();
        int lineNo = 0;
        for (String raw : text.split("\\R", -1)) {
            lineNo++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                throw new IllegalStateException("Invalid .env entry at line " + lineNo);
            }
            String key = line.substring(0, eq).trim();
            if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalStateException("Invalid .env key at line " + lineNo);
            }
            String value = line.substring(eq + 1).trim();
            if (value.length() >= 2 && value.charAt(0) == value.charAt(value.length() - 1)
                    && (value.charAt(0) == '"' || value.charAt(0) == '\'')) {
                value = value.substring(1, value.length() - 1);
            }
            entries.put(key, value);
        }
        return entries;
    }

    private static boolean alreadySet(String key) {
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return true;
        }
        String env = System.getenv(key);
        return env != null && !env.isBlank();
    }
}

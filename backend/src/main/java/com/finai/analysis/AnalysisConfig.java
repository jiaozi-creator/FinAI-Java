package com.finai.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 字段、规则和公式只从 YAML 读。读不到就启动失败，避免代码里再藏一套阈值。
 */
public final class AnalysisConfig {

    private static volatile AnalysisConfig instance;

    private final String fieldVersion;
    private final String ruleVersion;
    private final String formulaVersion;
    private final List<FieldSpec> fields;
    private final BigDecimal nonRecurringThreshold;
    private final BigDecimal nonRecurringHighThreshold;
    private final BigDecimal cashCoverageThreshold;
    private final Map<String, String> formulaExpressions;

    private AnalysisConfig(String fieldVersion, String ruleVersion, String formulaVersion, List<FieldSpec> fields,
                           BigDecimal nonRecurringThreshold, BigDecimal nonRecurringHighThreshold,
                           BigDecimal cashCoverageThreshold, Map<String, String> formulaExpressions) {
        this.fieldVersion = fieldVersion;
        this.ruleVersion = ruleVersion;
        this.formulaVersion = formulaVersion;
        this.fields = List.copyOf(fields);
        this.nonRecurringThreshold = nonRecurringThreshold;
        this.nonRecurringHighThreshold = nonRecurringHighThreshold;
        this.cashCoverageThreshold = cashCoverageThreshold;
        this.formulaExpressions = Map.copyOf(formulaExpressions);
    }

    public static AnalysisConfig get() {
        AnalysisConfig local = instance;
        if (local == null) {
            synchronized (AnalysisConfig.class) {
                local = instance;
                if (local == null) {
                    instance = local = load();
                }
            }
        }
        return local;
    }

    public String configVersion() {
        return "fields=" + fieldVersion + ",rules=" + ruleVersion + ",formulas=" + formulaVersion;
    }

    public String ruleVersion() {
        return ruleVersion;
    }

    public List<FieldSpec> fields() {
        return fields;
    }

    public BigDecimal nonRecurringThreshold() {
        return nonRecurringThreshold;
    }

    public BigDecimal nonRecurringHighThreshold() {
        return nonRecurringHighThreshold;
    }

    public BigDecimal cashCoverageThreshold() {
        return cashCoverageThreshold;
    }

    public String formulaExpression(String id) {
        return formulaExpressions.get(id);
    }

    private static AnalysisConfig load() {
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
        try {
            JsonNode fieldsRoot = yaml.readTree(open("field_dict.yaml"));
            JsonNode rulesRoot = yaml.readTree(open("rules.yaml"));
            JsonNode formulasRoot = yaml.readTree(open("formulas.yaml"));
            List<FieldSpec> fields = new ArrayList<>();
            for (JsonNode field : fieldsRoot.path("fields")) {
                List<String> aliases = new ArrayList<>();
                field.path("aliases").forEach(alias -> aliases.add(alias.asText()));
                if (field.path("id").asText().isBlank() || aliases.isEmpty()) {
                    throw new IllegalStateException("字段字典有空 id 或空别名");
                }
                fields.add(new FieldSpec(field.path("id").asText(), field.path("name").asText(), aliases));
            }
            if (fields.isEmpty()) {
                throw new IllegalStateException("字段字典没有 fields");
            }
            Map<String, String> formulas = new LinkedHashMap<>();
            for (JsonNode formula : formulasRoot.path("formulas")) {
                formulas.put(formula.path("id").asText(), formula.path("expression").asText(""));
            }
            return new AnalysisConfig(
                    requiredText(fieldsRoot, "version"),
                    requiredText(rulesRoot, "version"),
                    requiredText(formulasRoot, "version"),
                    fields,
                    ruleDecimal(rulesRoot, "non_recurring_gap", "threshold"),
                    ruleDecimal(rulesRoot, "non_recurring_gap", "high_threshold"),
                    ruleDecimal(rulesRoot, "profit_cashflow_divergence", "coverage_threshold"),
                    formulas);
        } catch (IOException e) {
            throw new IllegalStateException("读取分析配置失败", e);
        }
    }

    private static BigDecimal ruleDecimal(JsonNode root, String id, String field) {
        for (JsonNode rule : root.path("rules")) {
            if (id.equals(rule.path("id").asText())) {
                JsonNode value = rule.get(field);
                if (value == null || !value.isNumber()) {
                    throw new IllegalStateException("规则 " + id + " 缺少数字字段 " + field);
                }
                return value.decimalValue();
            }
        }
        throw new IllegalStateException("规则文件缺少 " + id);
    }

    private static String requiredText(JsonNode root, String field) {
        String value = root.path(field).asText();
        if (value.isBlank()) {
            throw new IllegalStateException("配置缺少 " + field);
        }
        return value;
    }

    static InputStream open(String name) throws IOException {
        Path[] candidates = new Path[]{
                Path.of("config", name),
                Path.of("..", "config", name),
                Path.of("..", "..", "config", name)
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return Files.newInputStream(candidate);
            }
        }
        InputStream classpath = AnalysisConfig.class.getClassLoader().getResourceAsStream("config/" + name);
        if (classpath == null) {
            throw new IllegalStateException("找不到配置 " + name + "。已查找 ./config、../config 和 classpath:/config");
        }
        return classpath;
    }
}

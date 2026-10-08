package com.finai.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.finai.model.dto.FinancialMetricsDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 金标准对照。抽取值、同比、比率和规则命中必须与 eval/gold 里手填的数一致。
 */
class GoldSetTest {

    @Test
    void demoStatementMatchesHandLabels() throws Exception {
        Path goldPath = Path.of("..", "eval", "gold", "demo-statement.yaml");
        if (!Files.isRegularFile(goldPath)) {
            goldPath = Path.of("eval", "gold", "demo-statement.yaml");
        }
        JsonNode gold = new ObjectMapper(new YAMLFactory()).readTree(goldPath.toFile());
        Path source = Path.of("..").resolve(gold.path("file").asText()).normalize();
        if (!Files.isRegularFile(source)) {
            source = Path.of(gold.path("file").asText());
        }

        StatementExtract extract = new StatementExtractor().extract(source.toString());
        FinancialMetricsDTO metrics = new MetricCalculator().calculate(extract.getLines(), "2024A");
        List<ArticulationCheck> checks = new MetricCalculator().articulate(extract.getLines());
        var signals = new AnomalyRuleEngine().detect(extract.getLines(), metrics, extract.getPolicyNotes());
        var valuation = new ValuationEngine().evaluate(extract.getLines(), metrics);

        Map<String, ExtractedLine> byField = extract.getLines().stream()
                .collect(Collectors.toMap(ExtractedLine::getFieldId, Function.identity(), (a, b) -> a));
        List<String> mismatches = new ArrayList<>();
        for (JsonNode field : gold.path("fields")) {
            String id = field.path("id").asText();
            ExtractedLine line = byField.get(id);
            if (line == null) {
                mismatches.add(id + " 未抽到");
                continue;
            }
            compare(mismatches, id + ".current", field.path("current").asText(), line.getCurrent());
            compare(mismatches, id + ".prior", field.path("prior").asText(), line.getPrior());
            if (!"万元".equals(line.getUnit())) {
                mismatches.add(id + " 单位是 " + line.getUnit() + "，金标准是万元");
            }
            if (!"consolidated".equals(line.getScope())) {
                mismatches.add(id + " 口径是 " + line.getScope() + "，金标准是 consolidated");
            }
        }
        gold.path("yoy").fields().forEachRemaining(entry ->
                compare(mismatches, "yoy." + entry.getKey(), entry.getValue().asText(), yoy(metrics, entry.getKey())));
        gold.path("ratios").fields().forEachRemaining(entry ->
                compare(mismatches, "ratio." + entry.getKey(), entry.getValue().asText(), ratio(metrics, entry.getKey())));
        if (checks.isEmpty() || !gold.path("articulation").asText().equals(checks.get(0).getStatus())) {
            mismatches.add("勾稽期望 " + gold.path("articulation").asText()
                    + "，实际 " + (checks.isEmpty() ? "未执行" : checks.get(0).getStatus()));
        }
        List<String> actualTypes = signals.stream().map(signal -> signal.getType().name()).toList();
        gold.path("anomalies").forEach(node -> {
            if (!actualTypes.contains(node.asText())) {
                mismatches.add("规则未命中 " + node.asText());
            }
        });
        if ("present".equals(gold.path("valuation_range").asText())
                && (valuation.getValuationRange() == null || valuation.getValuationRange().getMid() == null)) {
            mismatches.add("金标准要求有估值区间，实际没有");
        }
        assertThat(mismatches).as("金标准不一致").isEmpty();
    }

    private static void compare(List<String> mismatches, String name, String expected, BigDecimal actual) {
        if (expected == null || expected.isBlank() || "null".equals(expected)) {
            if (actual != null) {
                mismatches.add(name + " 期望空，实际 " + actual.toPlainString());
            }
            return;
        }
        if (actual == null) {
            mismatches.add(name + " 期望 " + expected + "，实际未提取");
            return;
        }
        if (new BigDecimal(expected).subtract(actual).abs().compareTo(new BigDecimal("0.0001")) > 0) {
            mismatches.add(name + " 期望 " + expected + "，实际 " + actual.toPlainString());
        }
    }

    private static BigDecimal yoy(FinancialMetricsDTO metrics, String id) {
        FinancialMetricsDTO.GrowthRates growth = metrics.getYoyGrowth();
        if (growth == null) {
            return null;
        }
        return switch (id) {
            case "revenue" -> growth.getRevenueGrowth();
            case "net_profit" -> growth.getNetProfitGrowth();
            default -> null;
        };
    }

    private static BigDecimal ratio(FinancialMetricsDTO metrics, String id) {
        FinancialMetricsDTO.FinancialRatios ratios = metrics.getRatios();
        if (ratios == null) {
            return null;
        }
        return switch (id) {
            case "gross_margin" -> ratios.getGrossMargin();
            case "roe" -> ratios.getRoe();
            default -> null;
        };
    }
}

package com.finai.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finai.model.dto.FinancialMetricsDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 模型只引用字段，数字由程序按字段、期间和单位格式化，内部结构不进入正文。 */
public final class ChatAnswerFormatter {
    private static final Pattern NUMBER = Pattern.compile("[-−+]?\\d[\\d,，]*(?:\\.\\d+)?");
    private ChatAnswerFormatter() {}
    public record Fact(String id, String label, BigDecimal value, String unit, String period, String scope, String status) {}
    public record Output(String text, List<String> fields) {}
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("revenue", "营业收入"), Map.entry("net_profit", "归母净利润"), Map.entry("net_profit_consolidated", "合并净利润"),
            Map.entry("net_profit_deducted", "扣非归母净利润"), Map.entry("net_margin", "归母净利率"), Map.entry("consolidated_net_margin", "合并净利率"),
            Map.entry("gross_margin", "毛利率"), Map.entry("disclosed_weighted_roe", "披露加权ROE"), Map.entry("average_equity_roe", "平均归母权益ROE"),
            Map.entry("operating_cash_flow", "经营现金流"), Map.entry("simple_free_cash_flow", "简式自由现金流"), Map.entry("ocf_to_net_profit", "经营现金流/归母净利润"),
            Map.entry("asset_liability_ratio", "资产负债率"), Map.entry("total_assets", "总资产"), Map.entry("total_liabilities", "总负债"), Map.entry("roa", "ROA"),
            Map.entry("cash", "货币资金"), Map.entry("restricted_cash", "受限资金"), Map.entry("basic_eps", "基本EPS"));

    public static Map<String, Fact> select(FinancialMetricsDTO metrics, String question) {
        Map<String, Fact> facts = new LinkedHashMap<>();
        if (metrics == null || metrics.getMetricDetails() == null) return facts;
        String q = question == null ? "" : question;
        List<String> ids = q.matches(".*(现金|偿债|债务).*" ) ? List.of("operating_cash_flow", "simple_free_cash_flow", "ocf_to_net_profit", "cash", "restricted_cash", "asset_liability_ratio", "net_profit")
                : q.matches(".*(盈利|利润|收益|增长).*" ) ? List.of("revenue", "net_profit", "net_profit_deducted", "gross_margin", "net_margin", "consolidated_net_margin", "disclosed_weighted_roe", "average_equity_roe", "roa", "basic_eps")
                : List.of("revenue", "net_profit", "operating_cash_flow", "asset_liability_ratio", "total_assets", "total_liabilities");
        for (String id : ids) {
            var detail = metrics.getMetricDetails().get(id);
            if (detail != null) facts.put(id, new Fact(id, LABELS.getOrDefault(id, id), detail.getValue(), detail.getUnit(), detail.getPeriod(), detail.getScope(), detail.getStatus()));
            var growth = metrics.getMetricDetails().get(id + "_growth");
            if (growth != null && (q.contains("增长") || q.contains("盈利") || q.contains("利润"))) facts.put(id + "_growth", new Fact(id + "_growth", LABELS.getOrDefault(id, id) + "同比", growth.getValue(), "%", growth.getPeriod(), growth.getScope(), growth.getStatus()));
        }
        return facts;
    }

    public static String material(Map<String, Fact> facts) {
        StringBuilder text = new StringBuilder();
        facts.values().forEach(f -> text.append(f.id()).append(" | ").append(f.label()).append(" | ").append(f.value()).append(" | ")
                .append(f.unit()).append(" | ").append(f.period()).append(" | ").append(f.scope()).append(" | ").append(f.status()).append('\n'));
        return text.toString();
    }

    public static Output render(String raw, Map<String, Fact> facts, boolean detailed, ObjectMapper mapper) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("EMPTY_REPLY");
        try {
            // 仅接受约定内部结构。文本中的裸数字一律拒绝，无小数字豁免。
            JsonNode root = mapper.readTree(raw.trim());
            if (root == null || !root.isObject() || !root.path("claims").isArray() || root.path("claims").isEmpty()) throw new IllegalArgumentException("FORMAT_ERROR");
            StringBuilder out = new StringBuilder();
            List<String> refs = new java.util.ArrayList<>();
            int max = detailed ? 12 : 4;
            for (JsonNode claim : root.path("claims")) {
                String id = claim.path("field").asText();
                Fact fact = facts.get(id);
                if (fact == null) throw new IllegalArgumentException("UNKNOWN_FIELD:" + id);
                String explanation = claim.path("text").asText("");
                if (NUMBER.matcher(explanation).find()) throw new IllegalArgumentException("NUMBER_REJECTED:" + id);
                if (explanation.contains("{") || explanation.contains("```") || explanation.length() > (detailed ? 250 : 45)) throw new IllegalArgumentException("FORMAT_ERROR");
                String unit = claim.path("unit").asText(fact.unit());
                if (claim.has("period") && !java.util.Objects.equals(fact.period(), claim.path("period").asText())) throw new IllegalArgumentException("PERIOD_REJECTED:" + id);
                if (claim.has("scope") && !java.util.Objects.equals(fact.scope(), claim.path("scope").asText())) throw new IllegalArgumentException("SCOPE_REJECTED:" + id);
                if (claim.has("value") && fact.value() != null && !matchesValue(fact, claim.path("value").asText(), unit)) throw new IllegalArgumentException("NUMBER_REJECTED:" + id);
                out.append(fact.label()).append(' ').append(format(fact, unit)).append('；').append(explanation).append('。');
                refs.add(id);
                if (refs.size() >= max) break;
            }
            if (!detailed && out.length() > 300) throw new IllegalArgumentException("ANSWER_TOO_LONG");
            return new Output(out.toString(), List.copyOf(refs));
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("FORMAT_ERROR", e); }
    }

    public static boolean matchesValue(Fact fact, String text, String unit) {
        try {
            BigDecimal expected = converted(fact, unit);
            BigDecimal proposed = new BigDecimal(text.replace(",", "").replace("，", ""));
            int scale = "%".equals(unit) || List.of("亿元", "万元", "倍").contains(unit) ? 2 : "元/股".equals(unit) ? Math.max(2, fact.value().scale()) : 2;
            return expected.setScale(scale, RoundingMode.HALF_UP).compareTo(proposed) == 0;
        } catch (Exception e) { return false; }
    }

    private static BigDecimal converted(Fact fact, String unit) {
        if (fact.value() == null) return null;
        if ("%".equals(fact.unit()) && "%".equals(unit)) return fact.value().movePointRight(2);
        if ("元".equals(fact.unit())) return switch (unit) {
            case "元" -> fact.value(); case "万元" -> fact.value().movePointLeft(4); case "亿元" -> fact.value().movePointLeft(8);
            default -> throw new IllegalArgumentException("UNIT_REJECTED");
        };
        if (java.util.Objects.equals(unit, fact.unit())) return fact.value();
        throw new IllegalArgumentException("UNIT_REJECTED");
    }

    private static String format(Fact fact, String unit) {
        if (fact.value() == null) return "数据不可用（" + fact.status() + "）";
        BigDecimal value = converted(fact, unit);
        int scale = "元/股".equals(unit) ? Math.max(2, fact.value().scale()) : "股".equals(unit) ? 0 : 2;
        return value.setScale(scale, RoundingMode.HALF_UP).toPlainString() + unit + "（" + fact.period() + "）";
    }

    public static Output fallback(Map<String, Fact> facts) {
        StringBuilder text = new StringBuilder("以下为当前报告中的确定性结果：");
        List<String> ids = new java.util.ArrayList<>();
        for (Fact fact : facts.values()) {
            if (fact.value() == null) continue;
            String unit = "元".equals(fact.unit()) ? "亿元" : fact.unit();
            text.append(fact.label()).append(' ').append(format(fact, unit)).append('；'); ids.add(fact.id());
            if (ids.size() >= 3) break;
        }
        if (ids.isEmpty()) text.append("该问题缺少可核验指标，请查看数据状态及证据。");
        else text.append("仅列财报事实，解释待复核。");
        return new Output(text.toString(), ids);
    }
}

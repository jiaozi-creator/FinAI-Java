package com.finai.analysis;

import com.finai.model.dto.AnomalySignalDTO;
import com.finai.model.dto.EvidenceDTO;
import com.finai.model.dto.FinancialMetricsDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AnomalyRuleEngine {

    public static final String RULE_VERSION = AnalysisConfig.get().ruleVersion();

    private final BigDecimal nonRecurringThreshold;
    private final BigDecimal nonRecurringHighThreshold;
    private final BigDecimal cashCoverageThreshold;

    public AnomalyRuleEngine() {
        AnalysisConfig config = AnalysisConfig.get();
        this.nonRecurringThreshold = config.nonRecurringThreshold();
        this.nonRecurringHighThreshold = config.nonRecurringHighThreshold();
        this.cashCoverageThreshold = config.cashCoverageThreshold();
    }

    public List<AnomalySignalDTO> detect(List<ExtractedLine> lines, FinancialMetricsDTO metrics, List<PolicyNote> notes) {
        Map<String, ExtractedLine> byField = lines.stream()
                .collect(Collectors.toMap(ExtractedLine::getFieldId, Function.identity(), (a, b) -> a));
        List<AnomalySignalDTO> signals = new ArrayList<>();
        nonRecurring(metrics, byField, signals);
        cashDivergence(metrics, byField, signals);
        policyChange(notes, signals);
        return signals;
    }

    private void nonRecurring(FinancialMetricsDTO metrics, Map<String, ExtractedLine> byField, List<AnomalySignalDTO> signals) {
        FinancialMetricsDTO.CoreMetrics core = metrics.getCoreMetrics();
        if (core == null || core.getNetProfit() == null || core.getNetProfitDeducted() == null) {
            signals.add(notEvaluated("ANO-NR-1", "非经常性损益占比", AnomalySignalDTO.AnomalyType.NON_RECURRING_ITEMS,
                    "缺少全年归母净利润或扣非归母净利润。", List.of("net_profit", "net_profit_deducted")));
            return;
        }
        if (!usable(byField.get("net_profit")) || !usable(byField.get("net_profit_deducted"))) {
            signals.add(notEvaluated("ANO-NR-1", "非经常性损益占比", AnomalySignalDTO.AnomalyType.NON_RECURRING_ITEMS,
                    "输入不是同口径的合并全年数据，规则不形成经营风险结论。", List.of("net_profit", "net_profit_deducted")));
            return;
        }
        if (core.getNetProfit().compareTo(BigDecimal.ZERO) == 0) {
            signals.add(notEvaluated("ANO-NR-1", "非经常性损益占比", AnomalySignalDTO.AnomalyType.NON_RECURRING_ITEMS,
                    "归母净利润为零，比例没有可解释性。", List.of("net_profit", "net_profit_deducted")));
            return;
        }
        BigDecimal gap = core.getNetProfit().subtract(core.getNetProfitDeducted()).abs();
        BigDecimal ratio = gap.divide(core.getNetProfit().abs(), 8, RoundingMode.HALF_UP);
        if (ratio.compareTo(NON_RECURRING_THRESHOLD) < 0) {
            signals.add(ruleResult("ANO-NR-1", "非经常性损益占比", AnomalySignalDTO.AnomalyType.NON_RECURRING_ITEMS,
                    "NOT_TRIGGERED", "VALID", ratio, NON_RECURRING_THRESHOLD,
                    "全年归母净利润与扣非归母净利润差额占比未达到阈值。",
                    List.of("net_profit", "net_profit_deducted"), byField));
            return;
        }
        AnomalySignalDTO.Severity severity = ratio.compareTo(nonRecurringHighThreshold) >= 0
                ? AnomalySignalDTO.Severity.HIGH
                : AnomalySignalDTO.Severity.MEDIUM;
        signals.add(AnomalySignalDTO.builder()
                .anomalyId("ANO-NR-1")
                .name("非经常性损益占比高")
                .type(AnomalySignalDTO.AnomalyType.NON_RECURRING_ITEMS)
                .severity(severity)
                .description("推论：|归母净利润-扣非净利润| / |归母净利润| = " + ratio.toPlainString()
                        + "，高于阈值 " + nonRecurringThreshold.toPlainString() + "。差额本身不是非经常性损益附注合计数。")
                .ruleTriggered("non_recurring_gap")
                .ruleVersion(RULE_VERSION)
                .resultStatus("TRIGGERED")
                .dataQualityStatus("VALID")
                .inputFields(List.of("net_profit", "net_profit_deducted"))
                .actualValue(ratio)
                .threshold(nonRecurringThreshold)
                .deviation(ratio.subtract(nonRecurringThreshold))
                .affectedMetrics(List.of("net_profit", "net_profit_deducted"))
                .evidence(List.of(evidence(byField.get("net_profit")), evidence(byField.get("net_profit_deducted"))))
                .verificationStatus(AnomalySignalDTO.VerificationStatus.PENDING)
                .verificationDetails("待核对非经常性损益附注。规则只比较主表两个净利润口径。")
                .recommendation("观点：评价主业时以扣非为准，并核对政府补助、资产处置和投资收益是否计入非经常性损益。")
                .build());
    }

    private void cashDivergence(FinancialMetricsDTO metrics, Map<String, ExtractedLine> byField, List<AnomalySignalDTO> signals) {
        FinancialMetricsDTO.CoreMetrics core = metrics.getCoreMetrics();
        if (core == null || core.getNetProfit() == null || core.getOperatingCashFlow() == null) {
            signals.add(notEvaluated("ANO-CF-1", "利润与现金流背离", AnomalySignalDTO.AnomalyType.PROFIT_CASHFLOW_DIVERGENCE,
                    "缺少全年归母净利润或全年经营现金流。", List.of("net_profit", "operating_cash_flow")));
            return;
        }
        if (!usable(byField.get("net_profit")) || !usable(byField.get("operating_cash_flow"))) {
            signals.add(notEvaluated("ANO-CF-1", "利润与现金流背离", AnomalySignalDTO.AnomalyType.PROFIT_CASHFLOW_DIVERGENCE,
                    "输入不是同口径的合并全年数据，规则不形成经营风险结论。", List.of("net_profit", "operating_cash_flow")));
            return;
        }
        if (core.getNetProfit().compareTo(BigDecimal.ZERO) <= 0) {
            signals.add(notEvaluated("ANO-CF-1", "利润与现金流背离", AnomalySignalDTO.AnomalyType.PROFIT_CASHFLOW_DIVERGENCE,
                    "归母净利润不为正，本规则不适用。", List.of("net_profit", "operating_cash_flow")));
            return;
        }
        BigDecimal coverage = core.getOperatingCashFlow().divide(core.getNetProfit(), 8, RoundingMode.HALF_UP);
        FinancialMetricsDTO.GrowthRates yoy = metrics.getYoyGrowth();
        boolean yoySplit = yoy != null && yoy.getNetProfitGrowth() != null && yoy.getOperatingCashFlowGrowth() != null
                && yoy.getNetProfitGrowth().compareTo(BigDecimal.ZERO) > 0
                && yoy.getOperatingCashFlowGrowth().compareTo(BigDecimal.ZERO) < 0;
        boolean signSplit = core.getOperatingCashFlow().compareTo(BigDecimal.ZERO) < 0;
        boolean thin = coverage.compareTo(cashCoverageThreshold) < 0;
        if (!signSplit && !thin && !yoySplit) {
            signals.add(ruleResult("ANO-CF-1", "利润与现金流背离", AnomalySignalDTO.AnomalyType.PROFIT_CASHFLOW_DIVERGENCE,
                    "NOT_TRIGGERED", "VALID", coverage, CASH_COVERAGE_THRESHOLD,
                    "全年经营现金流与归母净利润未达到背离阈值。",
                    List.of("net_profit", "operating_cash_flow"), byField));
            return;
        }
        AnomalySignalDTO.Severity severity = (signSplit || yoySplit)
                ? AnomalySignalDTO.Severity.HIGH
                : AnomalySignalDTO.Severity.MEDIUM;
        StringBuilder description = new StringBuilder("推论：盈利为正时经营现金流/归母净利润 = ");
        description.append(coverage.toPlainString()).append("。");
        if (signSplit) {
            description.append("利润为正、经营现金流为负。");
        }
        if (thin) {
            description.append("覆盖倍数低于 ").append(cashCoverageThreshold.toPlainString()).append("。");
        }
        if (yoySplit) {
            description.append("归母净利润同比增长，经营现金流同比下降。");
        }
        signals.add(AnomalySignalDTO.builder()
                .anomalyId("ANO-CF-1")
                .name("利润与现金流背离")
                .type(AnomalySignalDTO.AnomalyType.PROFIT_CASHFLOW_DIVERGENCE)
                .severity(severity)
                .description(description.toString())
                .ruleTriggered("profit_cashflow_divergence")
                .ruleVersion(RULE_VERSION)
                .resultStatus("TRIGGERED")
                .dataQualityStatus("VALID")
                .inputFields(List.of("net_profit", "operating_cash_flow"))
                .actualValue(coverage)
                .threshold(cashCoverageThreshold)
                .deviation(coverage.subtract(cashCoverageThreshold))
                .affectedMetrics(List.of("net_profit", "operating_cash_flow"))
                .evidence(List.of(evidence(byField.get("net_profit")), evidence(byField.get("operating_cash_flow"))))
                .verificationStatus(AnomalySignalDTO.VerificationStatus.PENDING)
                .verificationDetails("未区分营运资本变动和一次性现金项目，需对照现金流量表附注。")
                .recommendation("观点：先看经营现金流的应收、存货和预收分项，再判断利润含金量。")
                .build());
    }

    private void policyChange(List<PolicyNote> notes, List<AnomalySignalDTO> signals) {
        if (notes == null || notes.isEmpty()) {
            signals.add(notEvaluated("ANO-AP-1", "会计政策或估计变更", AnomalySignalDTO.AnomalyType.ACCOUNTING_POLICY_CHANGE,
                    "未定位到可核验的会计政策或估计变更附注。", List.of("accounting_policy")));
            return;
        }
        PolicyNote selected = notes.stream()
                .filter(note -> "CONFIRMED_CHANGE".equals(note.getJudgement()))
                .findFirst()
                .orElse(notes.get(0));
        boolean confirmed = "CONFIRMED_CHANGE".equals(selected.getJudgement());
        boolean none = "NO_MATERIAL_CHANGE".equals(selected.getJudgement());
        signals.add(AnomalySignalDTO.builder()
                .anomalyId("ANO-AP-1")
                .name("会计政策或估计变更")
                .type(AnomalySignalDTO.AnomalyType.ACCOUNTING_POLICY_CHANGE)
                .severity(confirmed ? AnomalySignalDTO.Severity.MEDIUM : AnomalySignalDTO.Severity.LOW)
                .description(confirmed
                        ? "推论：附注出现会计政策或估计变更，且措辞包含追溯、调整或新准则。"
                        : none
                        ? "推论：附注出现相关标题，文本同时表明未发生重大变更。不把标题本身当成变更事实。"
                        : "推论：附注提到会计政策或估计变更，措辞不足以确认是否实质变更。")
                .ruleTriggered("accounting_policy_mention")
                .ruleVersion(RULE_VERSION)
                .resultStatus(confirmed ? "TRIGGERED" : none ? "NOT_TRIGGERED" : "NOT_EVALUATED")
                .dataQualityStatus(confirmed || none ? "VALID" : "INPUT_INCOMPLETE")
                .inputFields(List.of("accounting_policy"))
                .affectedMetrics(List.of())
                .evidence(notes.stream().filter(note -> note == selected || "CONFIRMED_CHANGE".equals(note.getJudgement()))
                        .map(note -> EvidenceDTO.builder()
                        .fieldId("accounting_policy")
                        .fieldName("会计政策变更附注")
                        .page(note.getPage())
                        .snippet(note.getSnippet())
                        .confidence(confirmed ? 0.7 : 0.4)
                        .build()).toList())
                .verificationStatus(confirmed
                        ? AnomalySignalDTO.VerificationStatus.CONFIRMED
                        : AnomalySignalDTO.VerificationStatus.INCONCLUSIVE)
                .verificationDetails("是否发生=" + selected.getJudgement() + "；重要性及同比影响仍需按调整金额复核，不能由发生变更直接推定重大风险")
                .recommendation(confirmed
                        ? "先量化追溯调整对同比的影响；影响显著时才重算，影响不显著时在报告中披露差异。"
                        : "先取得准则附注和比较期调整金额，再决定是否重算同比。")
                .build());
    }

    private boolean usable(ExtractedLine line) {
        return line != null && line.getCurrent() != null && line.getPage() != null
                && line.getPage() > 0 && "consolidated".equals(line.getScope())
                && "annual".equals(line.getPeriodType())
                && line.getUnit() != null && !"未标注".equals(line.getUnit())
                && (line.getDataQualityStatus() == null
                    || "VALID".equals(line.getDataQualityStatus())
                    || "DISCLOSED_ZERO".equals(line.getDataQualityStatus()));
    }

    private AnomalySignalDTO notEvaluated(String id, String name, AnomalySignalDTO.AnomalyType type,
                                          String reason, List<String> fields) {
        BigDecimal configuredThreshold = switch (id) {
            case "ANO-NR-1" -> NON_RECURRING_THRESHOLD;
            case "ANO-CF-1" -> CASH_COVERAGE_THRESHOLD;
            default -> null;
        };
        return AnomalySignalDTO.builder()
                .anomalyId(id).name(name).type(type).severity(AnomalySignalDTO.Severity.LOW)
                .description("数据质量：" + reason)
                .ruleTriggered(id).ruleVersion(RULE_VERSION)
                .resultStatus("NOT_EVALUATED").dataQualityStatus("INPUT_INCOMPLETE")
                .threshold(configuredThreshold)
                .inputFields(fields).affectedMetrics(fields).evidence(List.of())
                .verificationStatus(AnomalySignalDTO.VerificationStatus.INCONCLUSIVE)
                .verificationDetails(reason)
                .recommendation("补齐同范围、同期间、同单位的财报证据后重新运行规则。")
                .build();
    }

    private AnomalySignalDTO ruleResult(String id, String name, AnomalySignalDTO.AnomalyType type,
                                        String status, String quality, BigDecimal actual, BigDecimal threshold,
                                        String description, List<String> fields,
                                        Map<String, ExtractedLine> byField) {
        return AnomalySignalDTO.builder()
                .anomalyId(id).name(name).type(type).severity(AnomalySignalDTO.Severity.LOW)
                .description(description).ruleTriggered(id).ruleVersion(RULE_VERSION)
                .resultStatus(status).dataQualityStatus(quality).inputFields(fields)
                .actualValue(actual).threshold(threshold).deviation(actual.subtract(threshold))
                .affectedMetrics(fields)
                .evidence(fields.stream().map(byField::get).map(this::evidence).filter(java.util.Objects::nonNull).toList())
                .verificationStatus(AnomalySignalDTO.VerificationStatus.CONFIRMED)
                .verificationDetails("规则输入口径校验通过。")
                .recommendation("未达到阈值，无需形成该项经营风险结论。")
                .build();
    }

    private EvidenceDTO evidence(ExtractedLine line) {
        if (line == null) {
            return null;
        }
        return EvidenceDTO.builder()
                .fieldId(line.getFieldId())
                .fieldName(line.getFieldName())
                .value(line.getCurrent() == null ? null : line.getCurrent().toPlainString())
                .unit(line.getUnit())
                .page(line.getPage())
                .snippet(line.getSnippet())
                .confidence(line.getConfidence())
                .sourceFile(line.getSourceFile())
                .build();
    }
}

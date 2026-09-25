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

    public static final String RULE_VERSION = "2026.1";
    private static final BigDecimal NON_RECURRING_THRESHOLD = new BigDecimal("0.20");
    private static final BigDecimal CASH_COVERAGE_THRESHOLD = new BigDecimal("0.50");

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
            return;
        }
        if (core.getNetProfit().compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        BigDecimal gap = core.getNetProfit().subtract(core.getNetProfitDeducted()).abs();
        BigDecimal ratio = gap.divide(core.getNetProfit().abs(), 8, RoundingMode.HALF_UP);
        if (ratio.compareTo(NON_RECURRING_THRESHOLD) < 0) {
            return;
        }
        AnomalySignalDTO.Severity severity = ratio.compareTo(new BigDecimal("0.50")) >= 0
                ? AnomalySignalDTO.Severity.HIGH
                : AnomalySignalDTO.Severity.MEDIUM;
        signals.add(AnomalySignalDTO.builder()
                .anomalyId("ANO-NR-1")
                .name("非经常性损益占比高")
                .type(AnomalySignalDTO.AnomalyType.NON_RECURRING_ITEMS)
                .severity(severity)
                .description("推论：|归母净利润-扣非净利润| / |归母净利润| = " + ratio.toPlainString()
                        + "，高于阈值 " + NON_RECURRING_THRESHOLD.toPlainString() + "。差额本身不是非经常性损益附注合计数。")
                .ruleTriggered("non_recurring_gap")
                .ruleVersion(RULE_VERSION)
                .actualValue(ratio)
                .threshold(NON_RECURRING_THRESHOLD)
                .deviation(ratio.subtract(NON_RECURRING_THRESHOLD))
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
            return;
        }
        if (core.getNetProfit().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal coverage = core.getOperatingCashFlow().divide(core.getNetProfit(), 8, RoundingMode.HALF_UP);
        FinancialMetricsDTO.GrowthRates yoy = metrics.getYoyGrowth();
        boolean yoySplit = yoy != null && yoy.getNetProfitGrowth() != null && yoy.getOperatingCashFlowGrowth() != null
                && yoy.getNetProfitGrowth().compareTo(BigDecimal.ZERO) > 0
                && yoy.getOperatingCashFlowGrowth().compareTo(BigDecimal.ZERO) < 0;
        boolean signSplit = core.getOperatingCashFlow().compareTo(BigDecimal.ZERO) < 0;
        boolean thin = coverage.compareTo(CASH_COVERAGE_THRESHOLD) < 0;
        if (!signSplit && !thin && !yoySplit) {
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
            description.append("覆盖倍数低于 ").append(CASH_COVERAGE_THRESHOLD.toPlainString()).append("。");
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
                .actualValue(coverage)
                .threshold(CASH_COVERAGE_THRESHOLD)
                .deviation(coverage.subtract(CASH_COVERAGE_THRESHOLD))
                .affectedMetrics(List.of("net_profit", "operating_cash_flow"))
                .evidence(List.of(evidence(byField.get("net_profit")), evidence(byField.get("operating_cash_flow"))))
                .verificationStatus(AnomalySignalDTO.VerificationStatus.PENDING)
                .verificationDetails("未区分营运资本变动和一次性现金项目，需对照现金流量表附注。")
                .recommendation("观点：先看经营现金流的应收、存货和预收分项，再判断利润含金量。")
                .build());
    }

    private void policyChange(List<PolicyNote> notes, List<AnomalySignalDTO> signals) {
        if (notes == null || notes.isEmpty()) {
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
                .affectedMetrics(List.of())
                .evidence(List.of(EvidenceDTO.builder()
                        .fieldId("accounting_policy")
                        .fieldName("会计政策变更附注")
                        .page(selected.getPage())
                        .snippet(selected.getSnippet())
                        .confidence(confirmed ? 0.7 : 0.4)
                        .build()))
                .verificationStatus(confirmed
                        ? AnomalySignalDTO.VerificationStatus.CONFIRMED
                        : AnomalySignalDTO.VerificationStatus.INCONCLUSIVE)
                .verificationDetails("页码 " + selected.getPage() + "，判断=" + selected.getJudgement())
                .recommendation("观点：若确认变更，需重算变更前后同一口径的同比，不能直接比较列示数。")
                .build());
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

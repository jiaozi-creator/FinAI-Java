package com.finai.agent;

import com.finai.model.dto.AnalysisReportDTO;
import com.finai.model.dto.ValuationResultDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Final deterministic guard against unsupported conclusions and malformed valuation output. */
@Component
public class ReviewAgent {
    public AgentResult review(AnalysisReportDTO report) {
        List<String> warnings = new ArrayList<>();
        if (report == null) warnings.add("REPORT_MISSING");
        if (report != null && report.getCitations() == null) warnings.add("CITATIONS_MISSING");
        if (report != null && report.getMetrics() != null) {
            var details = report.getMetrics().getMetricDetails();
            if (details == null) warnings.add("METRIC_DETAILS_MISSING");
            else details.forEach((id, detail) -> {
                if (detail.getValue() != null && (detail.getEvidenceRefs() == null || detail.getEvidenceRefs().isEmpty())) warnings.add("METRIC_EVIDENCE_MISSING:" + id);
                if (List.of("UNIT_MISMATCH", "PERIOD_MISMATCH", "SCOPE_MISMATCH").contains(detail.getStatus())) warnings.add("METRIC_CONFLICT:" + id);
                if (detail.getEvidenceRefs() != null && detail.getEvidenceRefs().stream().anyMatch(e -> e.getPage() == null || e.getPage() < 1 || e.getSnippet() == null || e.getSnippet().isBlank())) warnings.add("INVALID_EVIDENCE:" + id);
                if ("DISCLOSURE_MISMATCH".equals(detail.getStatus())) warnings.add("DISCLOSED_VALUE_MISMATCH:" + id);
                if (id.endsWith("_growth") && detail.getCurrentValue() != null && detail.getPriorValue() != null
                        && detail.getPriorValue().signum() != 0 && detail.getCalculatedValue() != null
                        && !detail.getCalculatedValue().equals(detail.getCurrentValue().subtract(detail.getPriorValue())
                        .divide(detail.getPriorValue().abs(), 8, java.math.RoundingMode.HALF_UP))) warnings.add("GROWTH_RECALCULATION_FAILED:" + id);
            });
            if (details != null) {
                verifyRatio(report, warnings, "asset_liability_ratio", "total_liabilities", "total_assets", false);
                verifyRatio(report, warnings, "roa", "net_profit_consolidated", "total_assets", true);
                verifyRatio(report, warnings, "average_equity_roe", "net_profit", "net_assets", true);
                verifyEquity(report, warnings);
            }
            verifyCashFlow(report.getMetrics(), warnings);
            var items = report.getMetrics().getNonRecurringItems();
            if (items != null) {
                var total = items.stream().filter(i -> "nri_total".equals(i.getFieldId())).findFirst();
                if (total.isPresent() && total.get().getAmount() != null) {
                    java.math.BigDecimal sum = items.stream().filter(i -> !"nri_total".equals(i.getFieldId()) && i.getAmount() != null)
                            .map(i -> List.of("nri_tax", "nri_minority_impact").contains(i.getFieldId()) ? i.getAmount().negate() : i.getAmount())
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    if (sum.subtract(total.get().getAmount()).abs().compareTo(new java.math.BigDecimal("0.01")) > 0) warnings.add("NON_RECURRING_RECONCILIATION_FAILED");
                }
            }
        } else if (report != null) warnings.add("METRICS_MISSING");
        if (report != null && report.getArticulationChecks() != null && report.getArticulationChecks().stream().anyMatch(c -> "FAIL".equals(c.getStatus()))) warnings.add("ARTICULATION_FAILED");
        ValuationResultDTO valuation = report == null ? null : report.getValuation();
        if (valuation != null && valuation.getValuationRange() == null
                && "COMPUTED".equals(valuation.getBusinessStatus())) {
            warnings.add("VALUATION_STATUS_INCONSISTENT");
        }
        if (valuation != null && valuation.getValuationRange() != null && !"COMPUTED".equals(valuation.getBusinessStatus())) warnings.add("VALUATION_STATUS_INCONSISTENT");
        if (valuation != null && valuation.getValuationRange() != null && valuation.getAssumptions() != null
                && valuation.getAssumptions().stream().anyMatch(a -> "default_unconfirmed".equals(a.getSource()))) warnings.add("VALUATION_UNCONFIRMED_INPUT");
        if (valuation != null && valuation.getDcfValuation() != null && valuation.getDcfValuation().getBaseCase() != null)
            verifyDcf(valuation, warnings);
        return AgentResult.builder()
                .status(warnings.isEmpty() ? "ok" : "degraded")
                .warnings(warnings)
                .nextAction(warnings.isEmpty() ? "complete" : "review")
                .build();
    }

    /** 独立复算明细，不信任上游状态、合计或残差标签，防止“PASSED”掩盖错期/重复行。 */
    private void verifyCashFlow(com.finai.model.dto.FinancialMetricsDTO metrics, List<String> warnings) {
        var r = metrics.getCashFlowReconciliation();
        if (r == null) { warnings.add("CASH_FLOW_RECONCILIATION_NOT_EXECUTED"); return; }
        if (!"RECONCILED".equals(r.getStatus())) warnings.add("CASH_FLOW_RECONCILIATION_FAILED:" + r.getStatus());
        if (r.getAdjustments() == null || r.getConsolidatedNetProfit() == null || r.getReportedOperatingCashFlow() == null) {
            warnings.add("CASH_FLOW_INPUT_INCOMPLETE"); return;
        }
        var ids = new java.util.HashSet<String>();
        java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
        for (var item : r.getAdjustments()) {
            if (item == null) { warnings.add("CASH_FLOW_ITEM_MISSING"); continue; }
            if (!ids.add(item.getFieldId())) warnings.add("CASH_FLOW_DUPLICATE_ITEM:" + item.getFieldId());
            if ("reconciliation_operating_cash_flow".equals(item.getFieldId()) || "reconciliation_net_profit".equals(item.getFieldId()))
                warnings.add("CASH_FLOW_RECONCILIATION_INCLUDES_REPORTED_CFO");
            if (item.getAmount() == null) warnings.add("CASH_FLOW_ITEM_VALUE_MISSING:" + item.getFieldId());
            else sum = sum.add(item.getAmount());
            var reconciliation = r;
            if (!java.util.Objects.equals(reconciliation.getCurrentPeriod(), item.getPeriod()) || !"consolidated".equals(item.getScope())
                    || !java.util.Objects.equals(reconciliation.getUnit(), item.getUnit())) warnings.add("CASH_FLOW_ITEM_CONTEXT_CONFLICT:" + item.getFieldId());
            if (item.getPage() == null || item.getPage() < 1 || item.getSnippet() == null || item.getSnippet().isBlank()
                    || item.getSourceFile() == null || item.getSourceFile().isBlank()) warnings.add("CASH_FLOW_ITEM_EVIDENCE_MISSING:" + item.getFieldId());
        }
        checkMoney(sum, r.getAdjustmentTotal(), "CASH_FLOW_ADJUSTMENT_SUM_FAILED", warnings);
        var calculated = r.getConsolidatedNetProfit().add(sum);
        checkMoney(calculated, r.getCalculatedOperatingCashFlow(), "CASH_FLOW_CALCULATED_TOTAL_FAILED", warnings);
        checkMoney(calculated, r.getReportedOperatingCashFlow(), "CASH_FLOW_CFO_RECALCULATION_FAILED", warnings);
        checkMoney(r.getReportedOperatingCashFlow().subtract(calculated), r.getResidual(), "CASH_FLOW_RESIDUAL_FAILED", warnings);
        if (metrics.getCoreMetrics() != null) {
            checkMoney(r.getReportedOperatingCashFlow(), metrics.getCoreMetrics().getOperatingCashFlow(), "CASH_FLOW_MAIN_STATEMENT_MISMATCH", warnings);
            checkMoney(r.getConsolidatedNetProfit(), metrics.getCoreMetrics().getNetProfitConsolidated(), "CASH_FLOW_PROFIT_MISMATCH", warnings);
        }
    }

    private void checkMoney(java.math.BigDecimal expected, java.math.BigDecimal actual, String code, List<String> warnings) {
        if (actual == null || expected.subtract(actual).abs().compareTo(new java.math.BigDecimal("0.01")) > 0) warnings.add(code);
    }

    private void verifyDcf(ValuationResultDTO value, List<String> warnings) {
        var dcf = value.getDcfValuation(); var base = dcf.getBaseCase();
        if (base.getPresentValues() == null) return; // 历史快照未执行新增逐年检查。
        java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
        for (int i = 0; i < base.getFreeCashFlows().size(); i++) {
            var expected = base.getFreeCashFlows().get(i).divide(java.math.BigDecimal.ONE.add(dcf.getWacc()).pow(i + 1), 8, java.math.RoundingMode.HALF_UP);
            if (expected.subtract(base.getPresentValues().get(i)).abs().compareTo(new java.math.BigDecimal("0.01")) > 0) warnings.add("DCF_DISCOUNT_FAILED:" + (i + 1));
            sum = sum.add(expected);
        }
        var terminal = base.getFreeCashFlows().get(base.getFreeCashFlows().size() - 1).multiply(java.math.BigDecimal.ONE.add(dcf.getTerminalGrowthRate()))
                .divide(dcf.getWacc().subtract(dcf.getTerminalGrowthRate()), 8, java.math.RoundingMode.HALF_UP);
        if (terminal.subtract(base.getTerminalValue()).abs().compareTo(new java.math.BigDecimal("0.01")) > 0) warnings.add("DCF_TERMINAL_FAILED");
        if (sum.add(base.getTerminalPresentValue()).subtract(base.getEnterpriseValue()).abs().compareTo(new java.math.BigDecimal("0.01")) > 0) warnings.add("DCF_EV_FAILED");
        if (!java.util.Objects.equals(value.getParameterVersion(), base.getParameterVersion())) warnings.add("DCF_VERSION_MISMATCH");
        var sensitivity = value.getSensitivityAnalysis();
        if (sensitivity != null && sensitivity.getValuationMatrix().size() == 5) {
            var center = sensitivity.getValuationMatrix().get(2).get(2);
            var expected = base.getEquityValue() == null ? base.getEnterpriseValue() : base.getEquityValue();
            if (center == null || center.subtract(expected).abs().compareTo(new java.math.BigDecimal("0.01")) > 0) warnings.add("DCF_SENSITIVITY_CENTER_FAILED");
        }
    }

    private void verifyRatio(AnalysisReportDTO report, List<String> warnings, String ratioId,
                             String numeratorId, String denominatorId, boolean average) {
        var details = report.getMetrics().getMetricDetails();
        var numerator = details.get(numeratorId);
        var denominator = details.get(denominatorId);
        var actual = details.get(ratioId);
        if (numerator == null || denominator == null) return;
        java.math.BigDecimal n = numerator.getValue();
        java.math.BigDecimal d = denominator.getValue();
        if (average) {
            var previous = denominator.getEvidenceRefs() == null ? null : denominator.getEvidenceRefs().stream()
                    .filter(e -> e.getPeriod() != null && e.getPeriod().equals(report.getMetrics().getPriorPeriod()))
                    .map(e -> new java.math.BigDecimal(e.getValue())).findFirst().orElse(null);
            if (previous == null || d == null) return;
            d = d.add(previous).divide(java.math.BigDecimal.valueOf(2), 8, java.math.RoundingMode.HALF_UP);
        }
        if (n == null || d == null || d.signum() == 0) return;
        if (actual == null || actual.getValue() == null) {
            warnings.add("COMPUTABLE_METRIC_MISSING:" + ratioId);
            return;
        }
        var expected = n.divide(d, 8, java.math.RoundingMode.HALF_UP);
        if (expected.subtract(actual.getValue()).abs().compareTo(new java.math.BigDecimal("0.00000001")) > 0)
            warnings.add("RATIO_RECALCULATION_FAILED:" + ratioId);
    }

    private void verifyEquity(AnalysisReportDTO report, List<String> warnings) {
        var details = report.getMetrics().getMetricDetails();
        var assets = details.get("total_assets"); var liabilities = details.get("total_liabilities");
        var equity = details.get("total_equity");
        if (assets == null || liabilities == null || equity == null || assets.getValue() == null || liabilities.getValue() == null || equity.getValue() == null) return;
        var residual = assets.getValue().subtract(liabilities.getValue()).subtract(equity.getValue()).abs();
        if (residual.compareTo(new java.math.BigDecimal("0.01")) > 0) warnings.add("TOTAL_EQUITY_RECONCILIATION_FAILED");
    }
}

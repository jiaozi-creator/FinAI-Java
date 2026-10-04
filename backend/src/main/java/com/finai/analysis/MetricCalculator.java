package com.finai.analysis;

import com.finai.model.dto.FinancialMetricsDTO;
import com.finai.model.dto.EvidenceDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MetricCalculator {

    private static final int SCALE = 8;
    private static final List<String> DISPLAY_FIELDS = List.of(
            "revenue", "net_profit", "net_profit_deducted", "operating_cash_flow",
            "total_assets", "total_liabilities", "net_assets", "minority_interest",
            "shares_outstanding", "basic_eps", "weighted_average_roe");

    public FinancialMetricsDTO calculate(List<ExtractedLine> lines, String period) {
        Map<String, ExtractedLine> byField = lines.stream()
                .collect(Collectors.toMap(ExtractedLine::getFieldId, Function.identity(), (a, b) -> a));

        FinancialMetricsDTO.CoreMetrics core = FinancialMetricsDTO.CoreMetrics.builder()
                .revenueTotal(current(byField, "revenue_total"))
                .revenue(current(byField, "revenue"))
                .netProfit(current(byField, "net_profit"))
                .netProfitConsolidated(current(byField, "net_profit_consolidated"))
                .netProfitDeducted(current(byField, "net_profit_deducted"))
                .operatingCashFlow(current(byField, "operating_cash_flow"))
                .capexProxy(current(byField, "capex"))
                .simpleFreeCashFlow(simpleFreeCashFlow(byField))
                .freeCashFlow(simpleFreeCashFlow(byField))
                .totalAssets(current(byField, "total_assets"))
                .totalLiabilities(current(byField, "total_liabilities"))
                .netAssets(current(byField, "net_assets"))
                .accountsReceivable(current(byField, "accounts_receivable"))
                .inventory(current(byField, "inventory"))
                .goodwill(current(byField, "goodwill"))
                .minorityInterest(current(byField, "minority_interest"))
                .totalEquity(current(byField, "total_equity"))
                .cash(current(byField, "cash"))
                .notesReceivable(current(byField, "notes_receivable"))
                .sharesOutstanding(current(byField, "shares_outstanding"))
                .basicEps(current(byField, "basic_eps"))
                .build();

        FinancialMetricsDTO.GrowthRates yoy = FinancialMetricsDTO.GrowthRates.builder()
                .revenueTotalGrowth(growth(byField, "revenue_total"))
                .revenueGrowth(growth(byField, "revenue"))
                .netProfitGrowth(growth(byField, "net_profit"))
                .netProfitDeductedGrowth(growth(byField, "net_profit_deducted"))
                .operatingCashFlowGrowth(growth(byField, "operating_cash_flow"))
                .simpleFreeCashFlowGrowth(simpleFreeCashFlowGrowth(byField))
                .totalAssetsGrowth(growth(byField, "total_assets"))
                .totalLiabilitiesGrowth(growth(byField, "total_liabilities"))
                .netAssetsGrowth(growth(byField, "net_assets"))
                .minorityInterestGrowth(growth(byField, "minority_interest"))
                .sharesOutstandingGrowth(growth(byField, "shares_outstanding"))
                .basicEpsGrowth(growth(byField, "basic_eps"))
                .build();

        return FinancialMetricsDTO.builder()
                .period(period)
                .priorPeriod(Periods.prior(period))
                .unit(commonUnit(lines))
                .scope(commonScope(lines))
                .coreMetrics(core)
                .yoyGrowth(yoy)
                .qoqGrowth(null)
                .ratios(ratios(byField))
                .nonRecurringItems(nonRecurring(lines))
                .metricDetails(metricDetails(byField, period))
                .build();
    }

    public List<ArticulationCheck> articulate(List<ExtractedLine> lines) {
        Map<String, ExtractedLine> byField = lines.stream()
                .collect(Collectors.toMap(ExtractedLine::getFieldId, Function.identity(), (a, b) -> a));
        BigDecimal assets = current(byField, "total_assets");
        BigDecimal liabilities = current(byField, "total_liabilities");
        BigDecimal equity = current(byField, "net_assets");
        BigDecimal minority = current(byField, "minority_interest");
        if (assets == null || liabilities == null || equity == null) {
            return List.of(ArticulationCheck.builder()
                    .name("资产=负债+权益")
                    .status("SKIPPED")
                    .detail("资产总计、负债合计或权益合计至少缺一项，勾稽跳过")
                    .statementType("事实")
                    .build());
        }
        if (!sameUnit(byField.get("total_assets"), byField.get("total_liabilities"))
                || !sameUnit(byField.get("total_assets"), byField.get("net_assets"))) {
            return List.of(ArticulationCheck.builder()
                    .name("资产=负债+权益")
                    .status("SKIPPED")
                    .detail("三项单位不一致，不比较")
                    .statementType("事实")
                    .build());
        }
        boolean withMinority = minority != null && sameUnit(byField.get("total_assets"), byField.get("minority_interest"));
        BigDecimal equityUsed = withMinority ? equity.add(minority) : equity;
        BigDecimal sum = liabilities.add(equityUsed);
        BigDecimal diff = assets.subtract(sum).abs();
        BigDecimal base = assets.abs().compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ONE : assets.abs();
        BigDecimal ratio = diff.divide(base, SCALE, RoundingMode.HALF_UP);
        boolean pass = ratio.compareTo(new BigDecimal("0.01")) <= 0;
        String name = withMinority ? "资产=负债+归母权益+少数股东权益" : "资产=负债+权益";
        String extra = withMinority ? "已计入少数股东权益。" : "未提取少数股东权益，权益只用归母。";
        return List.of(ArticulationCheck.builder()
                .name(name)
                .status(pass ? "PASS" : "FAIL")
                .detail("差额 " + diff.toPlainString() + "，相对资产 " + ratio.toPlainString() + "。" + extra)
                .statementType("事实")
                .build());
    }

    private static List<FinancialMetricsDTO.NonRecurringItem> nonRecurring(List<ExtractedLine> lines) {
        return lines.stream()
                .filter(line -> line.getFieldId() != null && line.getFieldId().startsWith("nri_"))
                .map(line -> FinancialMetricsDTO.NonRecurringItem.builder()
                        .fieldId(line.getFieldId())
                        .name(line.getFieldName())
                        .amount(line.getCurrent())
                        .page(line.getPage())
                        .snippet(line.getSnippet())
                        .build())
                .toList();
    }

    private FinancialMetricsDTO.FinancialRatios ratios(Map<String, ExtractedLine> byField) {
        BigDecimal revenue = current(byField, "revenue");
        BigDecimal cost = current(byField, "operating_cost");
        BigDecimal netProfit = current(byField, "net_profit");
        BigDecimal consolidatedNetProfit = current(byField, "net_profit_consolidated");
        BigDecimal assets = current(byField, "total_assets");
        BigDecimal liabilities = current(byField, "total_liabilities");
        BigDecimal equity = current(byField, "net_assets");
        BigDecimal ocf = current(byField, "operating_cash_flow");
        BigDecimal currentAssets = current(byField, "current_assets");
        BigDecimal currentLiabilities = current(byField, "current_liabilities");
        return FinancialMetricsDTO.FinancialRatios.builder()
                .grossMargin(compatible(byField, "revenue", "operating_cost", true) ? divide(revenue == null || cost == null ? null : revenue.subtract(cost), revenue) : null)
                .netMargin(compatible(byField, "net_profit", "revenue", true) ? divide(netProfit, revenue) : null)
                .consolidatedNetMargin(compatible(byField, "net_profit_consolidated", "revenue", true) ? divide(consolidatedNetProfit, revenue) : null)
                .roe(disclosedRoe(byField))
                .disclosedWeightedRoe(disclosedRoe(byField))
                .averageEquityRoe(compatible(byField, "net_profit", "net_assets", false) ? divide(netProfit, average(byField.get("net_assets"))) : null)
                .roa(compatible(byField, "net_profit_consolidated", "total_assets", false) ? divide(consolidatedNetProfit, average(byField.get("total_assets"))) : null)
                .assetLiabilityRatio(compatible(byField, "total_liabilities", "total_assets", true) ? divide(liabilities, assets) : null)
                .currentRatio(compatible(byField, "current_assets", "current_liabilities", true) ? divide(currentAssets, currentLiabilities) : null)
                .ocfToNetProfit(compatible(byField, "operating_cash_flow", "net_profit", true) ? divide(ocf, netProfit) : null)
                .build();
    }

    private BigDecimal simpleFreeCashFlow(Map<String, ExtractedLine> byField) {
        if (!compatible(byField, "operating_cash_flow", "capex", true)) {
            return null;
        }
        BigDecimal ocf = current(byField, "operating_cash_flow");
        BigDecimal capex = current(byField, "capex");
        if (ocf == null || capex == null) {
            return null;
        }
        return ocf.subtract(capex);
    }

    private BigDecimal simpleFreeCashFlowGrowth(Map<String, ExtractedLine> byField) {
        ExtractedLine ocf = byField.get("operating_cash_flow");
        ExtractedLine capex = byField.get("capex");
        if (!compatible(byField, "operating_cash_flow", "capex", true)
                || ocf == null || capex == null || ocf.getPrior() == null || capex.getPrior() == null) return null;
        BigDecimal current = ocf.getCurrent().subtract(capex.getCurrent());
        BigDecimal prior = ocf.getPrior().subtract(capex.getPrior());
        return prior.compareTo(BigDecimal.ZERO) == 0 ? null : current.subtract(prior).divide(prior.abs(), SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal disclosedRoe(Map<String, ExtractedLine> byField) {
        BigDecimal value = current(byField, "weighted_average_roe");
        return value == null ? null : value.divide(new BigDecimal("100"), SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal current(Map<String, ExtractedLine> byField, String fieldId) {
        ExtractedLine line = byField.get(fieldId);
        return line == null ? null : line.getCurrent();
    }

    private static BigDecimal average(ExtractedLine line) {
        if (line == null || line.getCurrent() == null || line.getPrior() == null) {
            return null;
        }
        return line.getCurrent().add(line.getPrior()).divide(new BigDecimal("2"), SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal growth(Map<String, ExtractedLine> byField, String fieldId) {
        ExtractedLine line = byField.get(fieldId);
        if (line == null || line.getCurrent() == null || line.getPrior() == null) {
            return null;
        }
        if (line.getPrior().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        if (!"consolidated".equals(line.getScope())
                || !("annual".equals(line.getPeriodType()) || "point_in_time".equals(line.getPeriodType()))) return null;
        return line.getCurrent().subtract(line.getPrior())
                .divide(line.getPrior().abs(), SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
    }

    private static boolean compatible(Map<String, ExtractedLine> byField, String left, String right, boolean requireSamePeriodType) {
        ExtractedLine a = byField.get(left);
        ExtractedLine b = byField.get(right);
        if (a == null || b == null) {
            return false;
        }
        if (!sameUnit(a, b) || !java.util.Objects.equals(a.getScope(), b.getScope())
                || !"consolidated".equals(a.getScope())) return false;
        return !requireSamePeriodType || java.util.Objects.equals(a.getPeriodType(), b.getPeriodType());
    }

    private static boolean sameUnit(ExtractedLine a, ExtractedLine b) {
        return a.getUnit() != null && a.getUnit().equals(b.getUnit());
    }

    private static String commonUnit(List<ExtractedLine> lines) {
        return lines.stream().map(ExtractedLine::getUnit).filter(u -> u != null && !"未标注".equals(u)).findFirst().orElse("未标注");
    }

    private static String commonScope(List<ExtractedLine> lines) {
        boolean consolidated = lines.stream().anyMatch(line -> "consolidated".equals(line.getScope()));
        if (consolidated) {
            return "consolidated";
        }
        return lines.isEmpty() ? "unknown" : lines.get(0).getScope();
    }

    private Map<String, FinancialMetricsDTO.MetricResult> metricDetails(Map<String, ExtractedLine> byField, String period) {
        Map<String, FinancialMetricsDTO.MetricResult> details = new LinkedHashMap<>();
        byField.forEach((id, line) -> details.put(id, detail(line.getCurrent(), line.getDataQualityStatus() == null ? "VALUE_AVAILABLE" : line.getDataQualityStatus(),
                line.getMissingReason(), null, List.of(id), line.getScope(), period, line.getUnit(), List.of(evidence(line)))));
        DISPLAY_FIELDS.forEach(id -> details.putIfAbsent(id, detail(null, "SOURCE_NOT_FOUND", "SOURCE_NOT_FOUND", null,
                List.of(id), "unknown", period, null, List.of())));
        addFormulaDetail(details, "simple_free_cash_flow", simpleFreeCashFlow(byField), "经营现金流－资本开支代理值",
                List.of("operating_cash_flow", "capex"), byField, period, "元");
        FinancialMetricsDTO.FinancialRatios ratios = ratios(byField);
        addFormulaDetail(details, "net_margin", ratios.getNetMargin(), "归母净利润/营业收入", List.of("net_profit", "revenue"), byField, period, "%");
        addFormulaDetail(details, "consolidated_net_margin", ratios.getConsolidatedNetMargin(), "合并净利润/营业收入", List.of("net_profit_consolidated", "revenue"), byField, period, "%");
        addFormulaDetail(details, "disclosed_weighted_roe", ratios.getDisclosedWeightedRoe(), "年报披露的加权平均净资产收益率", List.of("weighted_average_roe"), byField, period, "%");
        addFormulaDetail(details, "roa", ratios.getRoa(), "合并净利润/期初期末平均总资产", List.of("net_profit_consolidated", "total_assets"), byField, period, "%");
        addFormulaDetail(details, "average_equity_roe", ratios.getAverageEquityRoe(), "归母净利润/期初期末平均归母权益", List.of("net_profit", "net_assets"), byField, period, "%");
        addFormulaDetail(details, "gross_margin", ratios.getGrossMargin(), "(营业收入－营业成本)/营业收入", List.of("revenue", "operating_cost"), byField, period, "%");
        addFormulaDetail(details, "asset_liability_ratio", ratios.getAssetLiabilityRatio(), "负债合计/资产总计", List.of("total_liabilities", "total_assets"), byField, period, "%");
        addFormulaDetail(details, "current_ratio", ratios.getCurrentRatio(), "流动资产合计/流动负债合计", List.of("current_assets", "current_liabilities"), byField, period, "倍");
        addFormulaDetail(details, "ocf_to_net_profit", ratios.getOcfToNetProfit(), "经营活动现金流/归母净利润", List.of("operating_cash_flow", "net_profit"), byField, period, "倍");
        return details;
    }

    private void addFormulaDetail(Map<String, FinancialMetricsDTO.MetricResult> details, String id, BigDecimal value, String formula,
                                  List<String> inputs, Map<String, ExtractedLine> byField, String period, String unit) {
        List<ExtractedLine> found = inputs.stream().map(byField::get).filter(java.util.Objects::nonNull).toList();
        String reason = value != null ? null : found.size() < inputs.size() ? "INPUT_INCOMPLETE" : "SCOPE_PERIOD_OR_UNIT_MISMATCH";
        details.put(id, detail(value, value == null ? reason : "VALUE_AVAILABLE", reason, formula, inputs,
                found.isEmpty() ? "unknown" : found.get(0).getScope(), period, unit, found.stream().map(this::evidence).toList()));
    }

    private static FinancialMetricsDTO.MetricResult detail(BigDecimal value, String status, String reason, String formula,
                                                            List<String> inputs, String scope, String period, String unit,
                                                            List<EvidenceDTO> evidence) {
        return FinancialMetricsDTO.MetricResult.builder().value(value).status(status).missingReason(reason).formula(formula)
                .inputs(inputs).scope(scope).period(period).unit(unit).evidenceRefs(evidence).build();
    }

    private EvidenceDTO evidence(ExtractedLine line) {
        return EvidenceDTO.builder().fieldId(line.getFieldId()).fieldName(line.getFieldName())
                .value(line.getCurrent() == null ? null : line.getCurrent().toPlainString()).unit(line.getUnit())
                .page(line.getPage()).snippet(line.getSnippet()).confidence(line.getConfidence()).sourceFile(line.getSourceFile()).build();
    }
}

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
            "total_assets", "total_liabilities", "net_assets", "total_equity", "accounts_receivable", "minority_interest",
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
                .restrictedCash(current(byField, "restricted_cash"))
                .cashEquivalents(current(byField, "cash_equivalents"))
                .accruedDepositInterest(current(byField, "accrued_deposit_interest"))
                .restrictedInterbank(current(byField, "restricted_interbank"))
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
                .duPontAnalysis(duPont(byField))
                .cashFlowReconciliation(cashFlowReconciliation(byField))
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
                        .dataQualityStatus(line.getDataQualityStatus() == null ? "VALUE_AVAILABLE" : line.getDataQualityStatus())
                        .deductionRole(List.of("nri_tax", "nri_minority_impact").contains(line.getFieldId()) ? "SUBTRACT_FROM_GROSS_ITEMS" : "ADD_TO_GROSS_ITEMS")
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

    private FinancialMetricsDTO.DuPontAnalysis duPont(Map<String, ExtractedLine> fields) {
        BigDecimal margin = ratios(fields).getNetMargin();
        BigDecimal assets = average(fields.get("total_assets"));
        BigDecimal equity = average(fields.get("net_assets"));
        BigDecimal turnover = compatible(fields, "revenue", "total_assets", false) ? divide(current(fields, "revenue"), assets) : null;
        BigDecimal multiplier = compatible(fields, "total_assets", "net_assets", true) ? divide(assets, equity) : null;
        BigDecimal product = margin == null || turnover == null || multiplier == null ? null : margin.multiply(turnover).multiply(multiplier);
        return FinancialMetricsDTO.DuPontAnalysis.builder().netMargin(margin).averageAssetTurnover(turnover).averageEquityMultiplier(multiplier)
                .decomposedRoe(product).averageEquityRoe(ratios(fields).getAverageEquityRoe()).status(product == null ? "INPUT_INCOMPLETE" : "COMPUTED")
                .explanation("归母净利率 × 营业收入/平均总资产 × 平均总资产/平均归母权益；不替代披露加权ROE").build();
    }

    private FinancialMetricsDTO.CashFlowReconciliation cashFlowReconciliation(Map<String, ExtractedLine> fields) {
        BigDecimal profit = current(fields, "reconciliation_net_profit");
        var adjustmentLines = fields.values().stream().filter(l -> l.getFieldId().startsWith("reconciliation_")
                        && !"reconciliation_net_profit".equals(l.getFieldId())
                        && !"reconciliation_operating_cash_flow".equals(l.getFieldId()))
                .toList();
        var adjustments = adjustmentLines.stream()
                .map(l -> FinancialMetricsDTO.NonRecurringItem.builder().fieldId(l.getFieldId()).name(l.getFieldName())
                        .amount(l.getCurrent()).priorAmount(l.getPrior()).page(l.getPage()).snippet(l.getSnippet())
                        .dataQualityStatus(l.getDataQualityStatus() == null ? "VALUE_AVAILABLE" : l.getDataQualityStatus())
                        .period(l.getPeriodStart() == null ? null : l.getPeriodStart().substring(0, 4) + "A")
                        .scope(l.getScope()).unit(l.getUnit()).sourceFile(l.getSourceFile()).build()).toList();
        BigDecimal adjustmentTotal = adjustmentLines.stream().map(ExtractedLine::getCurrent)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sum = profit == null ? null : profit.add(adjustmentTotal);
        BigDecimal cfo = current(fields, "operating_cash_flow");
        BigDecimal residual = sum == null || cfo == null ? null : cfo.subtract(sum);
        String status = residual == null ? "INPUT_INCOMPLETE" : residual.abs().compareTo(new BigDecimal("0.01")) <= 0 ? "RECONCILED" : "RECONCILIATION_FAILED";
        return FinancialMetricsDTO.CashFlowReconciliation.builder().consolidatedNetProfit(profit).adjustmentTotal(adjustmentTotal)
                .adjustments(adjustments).calculatedOperatingCashFlow(sum)
                .reportedOperatingCashFlow(cfo).residual(residual).status(status)
                .currentPeriod(fields.values().stream().filter(l -> l.getFieldId().startsWith("reconciliation_")).map(ExtractedLine::getPeriodStart)
                        .filter(java.util.Objects::nonNull).findFirst().map(value -> value.substring(0, 4) + "A").orElse(null))
                .scope("consolidated")
                .unit(fields.get("operating_cash_flow") == null ? adjustmentLines.stream().map(ExtractedLine::getUnit)
                        .filter(java.util.Objects::nonNull).findFirst().orElse(null) : fields.get("operating_cash_flow").getUnit())
                .reason(status.equals("RECONCILED") ? "合并净利润加本期调节项目等于合并经营现金流；调节后 CFO 未重复计入" :
                        residual == null ? "缺少本期合并净利润、调节项目或披露 CFO，不能确认调节完整性" : "补充资料明细与披露 CFO 不符，残差表示尚未抽取或错列的净额；不得作为可信现金流归因").build();
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
        if (line != null && "basic_eps".equals(fieldId) && line.getDisclosedGrowth() != null) return line.getDisclosedGrowth();
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
        addFormulaDetail(details, "gross_margin", ratios.getGrossMargin(), "(合并营业收入－合并营业成本)/合并营业收入；派生毛利率，与年报酒类主营业务毛利率口径不同", List.of("revenue", "operating_cost"), byField, period, "%");
        addFormulaDetail(details, "asset_liability_ratio", ratios.getAssetLiabilityRatio(), "负债合计/资产总计", List.of("total_liabilities", "total_assets"), byField, period, "%");
        addFormulaDetail(details, "current_ratio", ratios.getCurrentRatio(), "流动资产合计/流动负债合计", List.of("current_assets", "current_liabilities"), byField, period, "倍");
        addFormulaDetail(details, "ocf_to_net_profit", ratios.getOcfToNetProfit(), "经营活动现金流/归母净利润", List.of("operating_cash_flow", "net_profit"), byField, period, "倍");
        for (String id : List.of("revenue_total", "revenue", "net_profit", "net_profit_deducted", "operating_cash_flow", "total_assets", "total_liabilities", "net_assets", "minority_interest", "shares_outstanding", "basic_eps")) {
            ExtractedLine line = byField.get(id);
            BigDecimal value = growth(byField, id);
            String reason = value != null ? null : line == null || line.getCurrent() == null ? "缺少本期值"
                    : line.getPrior() == null ? "缺少同口径上期值" : line.getPrior().signum() == 0 ? "上期值为零，同比无法定义"
                    : !"consolidated".equals(line.getScope()) ? "非合并报表范围" : "期间不是可比年度或年末时点";
            String status = value != null ? "VALUE_AVAILABLE" : reason.contains("范围") ? "SCOPE_MISMATCH"
                    : reason.contains("期间") ? "PERIOD_MISMATCH" : reason.contains("为零") ? "NOT_APPLICABLE" : "INPUT_INCOMPLETE";
            List<EvidenceDTO> refs = line == null ? List.of() : periodEvidence(line, period);
            var result = detail(value, status, reason, "(本期－调整后上期)/abs(调整后上期)", List.of(id), line == null ? "unknown" : line.getScope(), period, "%", refs);
            result.setSource(line != null && "basic_eps".equals(id) && line.getDisclosedGrowth() != null ? "extract" : "formula");
            result.setDisplayScale(2);
            if (line != null) {
                result.setCurrentValue(line.getCurrent());
                result.setPriorValue(line.getPrior());
                result.setDisclosedValue(line.getDisclosedGrowth());
                result.setCalculatedValue(calculatedGrowth(line));
                result.setCurrentColumn(line.getCurrentColumn());
                result.setPriorColumn(line.getPriorColumn());
                if ("basic_eps".equals(id) && line.getDisclosedGrowth() != null && result.getCalculatedValue() != null
                        && !epsRoundingCompatible(line.getCurrent(), line.getPrior(), line.getDisclosedGrowth())) {
                    result.setStatus("DISCLOSURE_MISMATCH");
                    result.setMissingReason("披露同比不在本期与上期 EPS 原披露精度形成的复算区间内");
                } else if ("basic_eps".equals(id) && line.getDisclosedGrowth() != null && result.getCalculatedValue() != null
                        && line.getDisclosedGrowth().compareTo(result.getCalculatedValue()) != 0) {
                    result.setPrecisionNote("披露同比与展示EPS复算存在差异；披露值落在原EPS精度复算区间，保留年报披露同比");
                }
            }
            details.put(id + "_growth", result);
        }
        addFormulaDetail(details, "simple_free_cash_flow_growth", simpleFreeCashFlowGrowth(byField), "(本期CFO-Capex－上期CFO+上期Capex)/abs(上期CFO-Capex)",
                List.of("operating_cash_flow", "capex"), byField, period, "%");
        var fcfGrowth = details.get("simple_free_cash_flow_growth");
        if (fcfGrowth.getValue() == null && byField.containsKey("operating_cash_flow") && byField.containsKey("capex")) {
            boolean missingPrior = byField.get("operating_cash_flow").getPrior() == null || byField.get("capex").getPrior() == null;
            if (missingPrior) { fcfGrowth.setStatus("INPUT_INCOMPLETE"); fcfGrowth.setMissingReason("缺少上期经营现金流或资本开支"); }
            else if (byField.get("operating_cash_flow").getPrior().subtract(byField.get("capex").getPrior()).signum() == 0) {
                fcfGrowth.setStatus("NOT_APPLICABLE"); fcfGrowth.setMissingReason("上期简式自由现金流为零，同比无法定义");
            }
        }
        details.forEach((id, result) -> {
            if (result.getDisplayScale() == null) result.setDisplayScale("shares_outstanding".equals(id) ? 0
                    : "basic_eps".equals(id) && result.getValue() != null ? Math.max(2, result.getValue().scale()) : 2);
            if (result.getSource() == null) result.setSource(result.getFormula() == null || "disclosed_weighted_roe".equals(id) ? "extract" : "formula");
            result.setEvidenceRefs(result.getInputs().stream().map(byField::get).filter(java.util.Objects::nonNull)
                    .flatMap(line -> periodEvidence(line, period).stream()).toList());
        });
        return details;
    }

    private void addFormulaDetail(Map<String, FinancialMetricsDTO.MetricResult> details, String id, BigDecimal value, String formula,
                                  List<String> inputs, Map<String, ExtractedLine> byField, String period, String unit) {
        List<ExtractedLine> found = inputs.stream().map(byField::get).filter(java.util.Objects::nonNull).toList();
        String status = "VALUE_AVAILABLE";
        String reason = null;
        if (value == null) {
            status = "INPUT_INCOMPLETE";
            reason = "缺少公式输入：" + inputs.stream().filter(key -> byField.get(key) == null || byField.get(key).getCurrent() == null).collect(Collectors.joining("、"));
            if (found.size() == inputs.size()) {
                if (found.stream().anyMatch(line -> !"consolidated".equals(line.getScope()))) { status = "SCOPE_MISMATCH"; reason = "公式输入不是同一合并范围"; }
                else if (found.stream().map(ExtractedLine::getUnit).distinct().count() > 1) { status = "UNIT_MISMATCH"; reason = "公式输入单位不一致"; }
                else if (found.stream().anyMatch(line -> "quarterly".equals(line.getPeriodType()))) { status = "PERIOD_MISMATCH"; reason = "公式输入包含季度或非可比期间"; }
                else if ((id.equals("roa") || id.equals("average_equity_roe")) && found.get(found.size() - 1).getPrior() == null) reason = "缺少期初余额，不能计算平均值";
                else { status = "NOT_APPLICABLE"; reason = "分母为零或缺少可比输入，无法计算"; }
            }
        }
        details.put(id, detail(value, status, reason, formula, inputs,
                found.isEmpty() ? "unknown" : found.get(0).getScope(), period, unit, found.stream().flatMap(line -> periodEvidence(line, period).stream()).toList()));
        var result = details.get(id);
        if (!found.isEmpty()) {
            result.setCurrentValue(found.get(0).getCurrent()); result.setPriorValue(found.get(0).getPrior());
            result.setCurrentColumn(found.get(0).getCurrentColumn()); result.setPriorColumn(found.get(0).getPriorColumn());
        }
    }

    private BigDecimal calculatedGrowth(ExtractedLine line) {
        if (line == null || line.getCurrent() == null || line.getPrior() == null || line.getPrior().signum() == 0) return null;
        return line.getCurrent().subtract(line.getPrior()).divide(line.getPrior().abs(), SCALE, RoundingMode.HALF_UP);
    }

    /** 用披露值舍入区间核验 EPS 同比，避免将年报精度差判为口径错误。 */
    static boolean epsRoundingCompatible(BigDecimal current, BigDecimal prior, BigDecimal disclosed) {
        if (current == null || prior == null || disclosed == null || current.signum() <= 0 || prior.signum() <= 0) return false;
        BigDecimal currentHalf = BigDecimal.ONE.scaleByPowerOfTen(-Math.max(0, current.scale())).divide(BigDecimal.valueOf(2));
        BigDecimal priorHalf = BigDecimal.ONE.scaleByPowerOfTen(-Math.max(0, prior.scale())).divide(BigDecimal.valueOf(2));
        BigDecimal min = current.subtract(currentHalf).subtract(prior.add(priorHalf))
                .divide(prior.add(priorHalf), SCALE, RoundingMode.HALF_UP);
        BigDecimal max = current.add(currentHalf).subtract(prior.subtract(priorHalf))
                .divide(prior.subtract(priorHalf), SCALE, RoundingMode.HALF_UP);
        return disclosed.compareTo(min) >= 0 && disclosed.compareTo(max) <= 0;
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

    private List<EvidenceDTO> periodEvidence(ExtractedLine line, String period) {
        EvidenceDTO current = evidence(line);
        current.setPeriod(period);
        if (line.getPrior() == null) return List.of(current);
        EvidenceDTO prior = evidence(line);
        prior.setPeriod(Periods.prior(period));
        prior.setValue(line.getPrior().toPlainString());
        return List.of(current, prior);
    }
}

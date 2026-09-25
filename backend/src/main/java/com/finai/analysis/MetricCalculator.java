package com.finai.analysis;

import com.finai.model.dto.FinancialMetricsDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class MetricCalculator {

    private static final int SCALE = 8;

    public FinancialMetricsDTO calculate(List<ExtractedLine> lines, String period) {
        Map<String, ExtractedLine> byField = lines.stream()
                .collect(Collectors.toMap(ExtractedLine::getFieldId, Function.identity(), (a, b) -> a));

        FinancialMetricsDTO.CoreMetrics core = FinancialMetricsDTO.CoreMetrics.builder()
                .revenue(current(byField, "revenue"))
                .netProfit(current(byField, "net_profit"))
                .netProfitDeducted(current(byField, "net_profit_deducted"))
                .operatingCashFlow(current(byField, "operating_cash_flow"))
                .freeCashFlow(freeCashFlow(byField))
                .totalAssets(current(byField, "total_assets"))
                .totalLiabilities(current(byField, "total_liabilities"))
                .netAssets(current(byField, "net_assets"))
                .accountsReceivable(current(byField, "accounts_receivable"))
                .inventory(current(byField, "inventory"))
                .goodwill(current(byField, "goodwill"))
                .minorityInterest(current(byField, "minority_interest"))
                .sharesOutstanding(current(byField, "shares_outstanding"))
                .basicEps(current(byField, "basic_eps"))
                .build();

        FinancialMetricsDTO.GrowthRates yoy = FinancialMetricsDTO.GrowthRates.builder()
                .revenueGrowth(growth(byField, "revenue"))
                .netProfitGrowth(growth(byField, "net_profit"))
                .netProfitDeductedGrowth(growth(byField, "net_profit_deducted"))
                .operatingCashFlowGrowth(growth(byField, "operating_cash_flow"))
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
        BigDecimal assets = current(byField, "total_assets");
        BigDecimal liabilities = current(byField, "total_liabilities");
        BigDecimal equity = current(byField, "net_assets");
        BigDecimal ocf = current(byField, "operating_cash_flow");
        BigDecimal currentAssets = current(byField, "current_assets");
        BigDecimal currentLiabilities = current(byField, "current_liabilities");
        return FinancialMetricsDTO.FinancialRatios.builder()
                .grossMargin(same(byField, "revenue", "operating_cost") ? divide(revenue == null || cost == null ? null : revenue.subtract(cost), revenue) : null)
                .netMargin(same(byField, "net_profit", "revenue") ? divide(netProfit, revenue) : null)
                .roe(same(byField, "net_profit", "net_assets") ? divide(netProfit, equity) : null)
                .roa(same(byField, "net_profit", "total_assets") ? divide(netProfit, assets) : null)
                .assetLiabilityRatio(same(byField, "total_liabilities", "total_assets") ? divide(liabilities, assets) : null)
                .currentRatio(same(byField, "current_assets", "current_liabilities") ? divide(currentAssets, currentLiabilities) : null)
                .ocfToNetProfit(same(byField, "operating_cash_flow", "net_profit") ? divide(ocf, netProfit) : null)
                .build();
    }

    private BigDecimal freeCashFlow(Map<String, ExtractedLine> byField) {
        if (!same(byField, "operating_cash_flow", "capex")) {
            return null;
        }
        BigDecimal ocf = current(byField, "operating_cash_flow");
        BigDecimal capex = current(byField, "capex");
        if (ocf == null || capex == null) {
            return null;
        }
        return ocf.subtract(capex);
    }

    private static BigDecimal current(Map<String, ExtractedLine> byField, String fieldId) {
        ExtractedLine line = byField.get(fieldId);
        return line == null ? null : line.getCurrent();
    }

    private static BigDecimal growth(Map<String, ExtractedLine> byField, String fieldId) {
        ExtractedLine line = byField.get(fieldId);
        if (line == null || line.getCurrent() == null || line.getPrior() == null) {
            return null;
        }
        if (line.getPrior().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return line.getCurrent().subtract(line.getPrior())
                .divide(line.getPrior().abs(), SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP);
    }

    private static boolean same(Map<String, ExtractedLine> byField, String left, String right) {
        ExtractedLine a = byField.get(left);
        ExtractedLine b = byField.get(right);
        if (a == null || b == null) {
            return false;
        }
        return sameUnit(a, b);
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
}

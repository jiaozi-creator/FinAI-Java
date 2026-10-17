package com.finai.analysis;

import com.finai.model.dto.FinancialMetricsDTO;
import com.finai.model.dto.ValuationResultDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 逐年标准 FCFF：经营范围、来源、年末折现及桥接分层门禁。 */
public final class AnnualDcfModel {
    private AnnualDcfModel() {}
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static BigDecimal decimal(String text) { return new BigDecimal(text); }

    public static List<String> inputErrors(ValuationParameters p) {
        List<String> errors = new ArrayList<>();
        if (!p.isAssumptionsConfirmed()) errors.add("预测和折现参数未经确认");
        if (!List.of("LIQUOR_OPERATIONS", "WHOLE_GROUP").contains(p.getValuationScope() == null ? "" : p.getValuationScope())) errors.add("须选择酒类经营业务或整个集团估值范围");
        if (blank(p.getFinancialCompanyTreatment())) errors.add("财务公司处理依据未提供（无财务公司也须说明）");
        if (blank(p.getForecastSource())) errors.add("预测来源未提供");
        if (p.getYears() < 1 || p.getYears() > 30) errors.add("预测期须为1至30年");
        if (p.getWacc() == null || p.getWacc().signum() <= 0 || p.getTerminalGrowth() == null
                || p.getTerminalGrowth().compareTo(decimal("-1")) <= 0 || p.getWacc().compareTo(p.getTerminalGrowth()) <= 0) errors.add("折现参数无效，须 WACC > g 且 WACC > 0、g > -1");
        if (!List.of("DIRECT", "COMPONENTS").contains(p.getInputMode() == null ? "" : p.getInputMode())) errors.add("须选择逐年直接或分项 FCFF 模式");
        if ("COMPONENTS".equals(p.getInputMode())) {
            for (String id : List.of("ebit", "taxRate", "depreciation", "capex", "workingCapitalIncrease")) {
                if (p.getInputDefinitions() == null || blank(p.getInputDefinitions().get(id))) errors.add(id + " 定义、经营范围及调整依据未提供");
            }
        }
        validatePath(p, p.getForecast(), "基准", errors);
        if (p.getScenarios() != null) p.getScenarios().forEach((name, path) -> validatePath(p, path, name, errors));
        return errors;
    }

    private static void validatePath(ValuationParameters p, List<ValuationParameters.ForecastYear> path, String name, List<String> errors) {
        if (path == null || path.size() != p.getYears()) { errors.add(name + "预测年度数与预测期不一致"); return; }
        for (int i = 0; i < path.size(); i++) {
            var row = path.get(i);
            if (row == null) { errors.add(name + "年度输入为空"); continue; }
            if (row.getYear() != i + 1 || blank(row.getSource()) || row.cashFlow(p.getInputMode()) == null) errors.add(name + "第" + (i + 1) + "年输入、顺序或依据不完整");
            if ("COMPONENTS".equals(p.getInputMode()) && (row.getTaxRate() == null || row.getTaxRate().signum() < 0 || row.getTaxRate().compareTo(ONE) > 0
                    || row.getDepreciation() == null || row.getDepreciation().signum() < 0 || row.getCapex() == null || row.getCapex().signum() < 0)) errors.add(name + "第" + (i + 1) + "年税率、折旧或资本开支无效");
        }
    }

    public static List<String> bridgeErrors(ValuationParameters p) {
        List<String> errors = new ArrayList<>();
        if (!p.isBridgePolicyConfirmed() || blank(p.getBridgePolicy())) errors.add("桥接政策未经确认");
        BigDecimal[] values = {p.getUsableCash(), p.getGrossDebt(), p.getMinorityAdjustment(), p.getNonCoreAssets(), p.getDilutedShares()};
        String[] names = {"usableCash", "grossDebt", "minorityAdjustment", "nonCoreAssets", "dilutedShares"};
        for (int i = 0; i < names.length; i++) {
            if (values[i] == null || values[i].signum() < 0 || i == 4 && values[i].signum() <= 0) errors.add(names[i] + "金额未确认或无效");
            if (p.getBridgeSources() == null || blank(p.getBridgeSources().get(names[i]))) errors.add(names[i] + "范围、来源或零调整理由未提供");
        }
        return errors;
    }

    public static ValuationResultDTO evaluate(ValuationParameters p, FinancialMetricsDTO metrics) {
        return evaluate(p, metrics, List.of());
    }

    public static ValuationResultDTO evaluate(ValuationParameters p, FinancialMetricsDTO metrics, List<ExtractedLine> lines) {
        List<String> inputErrors = inputErrors(p);
        List<String> bridgeErrors = bridgeErrors(p);
        List<ValuationResultDTO.ValuationAssumption> assumptions = new ArrayList<>();
        add(assumptions, "估值范围", p.getValuationScope(), p.getFinancialCompanyTreatment());
        add(assumptions, "标准FCFF预测", p.getInputMode(), p.getForecastSource());
        add(assumptions, "WACC", String.valueOf(p.getWacc()), "年末折现；确认版本 " + p.getVersion());
        add(assumptions, "永续增长率", String.valueOf(p.getTerminalGrowth()), "同一冻结预测路径计算终值");
        add(assumptions, "桥接政策", p.getBridgePolicy(), "未确认时只显示经营EV，不输出普通股估值");
        if (p.getBridgeSources() != null) p.getBridgeSources().forEach((id, source) -> add(assumptions, id, source, "逐项来源及范围"));
        ValuationResultDTO result = ValuationResultDTO.builder().parameterVersion(p.getVersion()).valuationScope(p.getValuationScope())
                .forecastInputs(p.getForecast()).bridgeSources(p.getBridgeSources()).assumptions(assumptions)
                .inputStatus(inputErrors.isEmpty() && bridgeErrors.isEmpty() ? "COMPLETE" : "INPUT_INCOMPLETE")
                .calculationStatus("NOT_EXECUTED").businessStatus("INPUT_INCOMPLETE").build();
        Map<String, ExtractedLine> fields = lines.stream().collect(java.util.stream.Collectors.toMap(ExtractedLine::getFieldId, x -> x, (a,b) -> a));
        NetDebtDiagnostics.attach(result, fields, p);
        if (metrics != null && metrics.getCoreMetrics() != null && !blank(p.getMultipleSource())) {
            var core = metrics.getCoreMetrics();
            if (p.getPeMultiple() != null && p.getPeMultiple().signum() > 0 && core.getBasicEps() != null) result.setAssumedPePrice(p.getPeMultiple().multiply(core.getBasicEps()));
            if (p.getPbMultiple() != null && p.getPbMultiple().signum() > 0 && core.getNetAssets() != null && core.getSharesOutstanding() != null && core.getSharesOutstanding().signum() > 0)
                result.setAssumedPbPrice(p.getPbMultiple().multiply(core.getNetAssets()).divide(core.getSharesOutstanding(), 8, RoundingMode.HALF_UP));
            result.setMultipleSource(p.getMultipleSource());
        }
        if (!inputErrors.isEmpty()) { result.setBlockReason(String.join("；", inputErrors) + "；" + String.join("；", bridgeErrors)); return result; }
        BigDecimal adjustment = bridgeErrors.isEmpty() ? p.getGrossDebt().subtract(p.getUsableCash()).add(p.getMinorityAdjustment()).subtract(p.getNonCoreAssets()) : null;
        var base = scenario("基准", p.getForecast(), p, p.getWacc(), p.getTerminalGrowth(), adjustment);
        var optimistic = pathScenario("乐观", p, adjustment);
        var pessimistic = pathScenario("悲观", p, adjustment);
        result.setDcfValuation(ValuationResultDTO.DCFValuation.builder().baseCase(base).optimistic(optimistic).pessimistic(pessimistic)
                .wacc(p.getWacc()).terminalGrowthRate(p.getTerminalGrowth()).build());
        result.setSensitivityAnalysis(sensitivity(p, adjustment));
        result.setCalculationStatus("COMPUTED");
        result.setBusinessStatus(adjustment == null ? "EV_COMPUTED_BRIDGE_PENDING" : "COMPUTED");
        if (adjustment == null) result.setBlockReason(String.join("；", bridgeErrors));
        BigDecimal value = adjustment == null ? base.getEnterpriseValue() : base.getEquityValue();
        if (adjustment != null) {
            BigDecimal low = value; BigDecimal high = value;
            for (var scenario : new ValuationResultDTO.DCFValuation.Scenario[]{optimistic, pessimistic}) if (scenario != null) { low = low.min(scenario.getEquityValue()); high = high.max(scenario.getEquityValue()); }
            result.setValuationRange(ValuationResultDTO.ValuationRange.builder().low(low).mid(value).high(high)
                    .methodology("经明确确认的逐年标准FCFF，年末折现；未提供的情景不自动生成")
                    .applicabilityNote("用户假设结果，无市价比较或投资评级；参数版本 " + p.getVersion()).build());
        }
        if (base.getTerminalShare() != null) add(assumptions, "终值现值占比", base.getTerminalShare().toPlainString(), "按同一基准参数实际计算");
        if (p.getForecast().get(p.getYears() - 1).cashFlow(p.getInputMode()).signum() <= 0) add(assumptions, "终年现金流边界", "终年FCFF非正", "不支持正向持续经营价值；负终值原样展示，须人工复核");
        return result;
    }

    private static ValuationResultDTO.DCFValuation.Scenario pathScenario(String name, ValuationParameters p, BigDecimal adjustment) {
        var path = p.getScenarios() == null ? null : p.getScenarios().get(name);
        return path == null ? null : scenario(name, path, p, p.getWacc(), p.getTerminalGrowth(), adjustment);
    }

    static ValuationResultDTO.DCFValuation.Scenario scenario(String name, List<ValuationParameters.ForecastYear> path,
                                                           ValuationParameters p, BigDecimal wacc, BigDecimal g, BigDecimal adjustment) {
        List<BigDecimal> flows = new ArrayList<>(), factors = new ArrayList<>(), values = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < path.size(); i++) {
            BigDecimal flow = path.get(i).cashFlow(p.getInputMode());
            BigDecimal factor = ONE.divide(ONE.add(wacc).pow(i + 1), 16, RoundingMode.HALF_UP);
            BigDecimal pv = flow.divide(ONE.add(wacc).pow(i + 1), 8, RoundingMode.HALF_UP);
            flows.add(flow); factors.add(factor); values.add(pv); sum = sum.add(pv);
        }
        BigDecimal terminal = flows.get(flows.size() - 1).multiply(ONE.add(g)).divide(wacc.subtract(g), 8, RoundingMode.HALF_UP);
        BigDecimal terminalPv = terminal.divide(ONE.add(wacc).pow(path.size()), 8, RoundingMode.HALF_UP);
        BigDecimal ev = sum.add(terminalPv);
        BigDecimal equity = adjustment == null ? null : ev.subtract(adjustment);
        return ValuationResultDTO.DCFValuation.Scenario.builder().name(name).parameterVersion(p.getVersion()).freeCashFlows(flows)
                .discountFactors(factors).presentValues(values).terminalValue(terminal).terminalPresentValue(terminalPv)
                .terminalShare(ev.signum() == 0 ? null : terminalPv.divide(ev, 8, RoundingMode.HALF_UP))
                .enterpriseValue(ev).equityValue(equity).valuePerShare(equity == null ? null : equity.divide(p.getDilutedShares(), 8, RoundingMode.HALF_UP)).build();
    }

    private static ValuationResultDTO.SensitivityAnalysis sensitivity(ValuationParameters p, BigDecimal adjustment) {
        List<BigDecimal> ws = List.of(decimal("-0.02"), decimal("-0.01"), BigDecimal.ZERO, decimal("0.01"), decimal("0.02")).stream().map(p.getWacc()::add).toList();
        List<BigDecimal> gs = List.of(decimal("-0.01"), decimal("-0.005"), BigDecimal.ZERO, decimal("0.005"), decimal("0.01")).stream().map(p.getTerminalGrowth()::add).toList();
        List<List<BigDecimal>> matrix = new ArrayList<>();
        for (BigDecimal w : ws) {
            List<BigDecimal> row = new ArrayList<>();
            for (BigDecimal g : gs) {
                if (w.signum() <= 0 || w.compareTo(g) <= 0 || g.compareTo(decimal("-1")) <= 0) row.add(null);
                else { var cell = scenario("敏感性", p.getForecast(), p, w, g, adjustment); row.add(adjustment == null ? cell.getEnterpriseValue() : cell.getEquityValue()); }
            }
            matrix.add(row);
        }
        return ValuationResultDTO.SensitivityAnalysis.builder().waccRange(ws).terminalGrowthRange(gs).valuationMatrix(matrix).build();
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static void add(List<ValuationResultDTO.ValuationAssumption> list, String name, String value, String source) {
        list.add(ValuationResultDTO.ValuationAssumption.builder().parameter(name).value(value).source("user_confirmed").rationale(source).build());
    }
}

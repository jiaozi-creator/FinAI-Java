package com.finai.analysis;

import com.finai.model.dto.FinancialMetricsDTO;
import com.finai.model.dto.ValuationResultDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ValuationEngine {

    private static final int YEARS = 5;
    private static final BigDecimal WACC = new BigDecimal("0.09");
    private static final BigDecimal TERMINAL_G = new BigDecimal("0.02");
    private static final BigDecimal G_CAP = new BigDecimal("0.30");
    private static final BigDecimal G_FLOOR = new BigDecimal("-0.30");

    public ValuationResultDTO evaluate(List<ExtractedLine> lines, FinancialMetricsDTO metrics) {
        return evaluate(lines, metrics, null, null);
    }

    /**
     * confirmedGrowth 只表示用户确认的预测增速。简式自由现金流不能自动升级为标准 FCFF。
     */
    public ValuationResultDTO evaluate(List<ExtractedLine> lines, FinancialMetricsDTO metrics, BigDecimal confirmedGrowth) {
        return evaluate(lines, metrics, confirmedGrowth, null);
    }

    /** 只有显式提供经确认的标准 FCFF 与增速时，才允许执行正式 DCF。 */
    public ValuationResultDTO evaluate(List<ExtractedLine> lines, FinancialMetricsDTO metrics,
                                       BigDecimal confirmedGrowth, BigDecimal confirmedFcff) {
        return evaluate(lines, metrics, ValuationParameters.builder()
                .confirmedGrowth(confirmedGrowth).confirmedFcff(confirmedFcff).build());
    }

    public ValuationResultDTO evaluate(List<ExtractedLine> lines, FinancialMetricsDTO metrics,
                                       ValuationParameters parameters) {
        if (parameters == null) parameters = ValuationParameters.builder().build();
        if (parameters.getInputMode() != null || parameters.getForecast() != null) return AnnualDcfModel.evaluate(parameters, metrics, lines);
        Map<String, ExtractedLine> byField = lines.stream()
                .collect(Collectors.toMap(ExtractedLine::getFieldId, Function.identity(), (a, b) -> a));
        ValuationResultDTO diagnostic = NetDebtDiagnostics.attach(new ValuationResultDTO(), byField, parameters);
        BigDecimal wacc = parameters.getWacc();
        BigDecimal terminalGrowth = parameters.getTerminalGrowth();
        int years = parameters.getYears();
        if (wacc == null || terminalGrowth == null || years < 1 || wacc.compareTo(terminalGrowth) <= 0) {
            return withDiagnostic(blocked("BLOCKED_BY_DATA_QUALITY", "WACC、终值增长率或预测期不满足模型边界。", new ArrayList<>()), diagnostic);
        }
        List<ValuationResultDTO.ValuationAssumption> assumptions = new ArrayList<>();
        FinancialMetricsDTO.CoreMetrics core = metrics.getCoreMetrics();
        BigDecimal ocf = core == null ? null : core.getOperatingCashFlow();
        BigDecimal capex = current(byField, "capex");
        if (ocf == null || capex == null || !sameAnnualConsolidated(byField.get("operating_cash_flow"), byField.get("capex"))) {
            assumptions.add(assumption("简式自由现金流", "未计算", "formula", "缺少同年度、同范围、同单位资本开支。"));
        } else {
            BigDecimal simpleFcf = ocf.subtract(capex);
            assumptions.add(assumption("简式自由现金流", simpleFcf.toPlainString(), "formula",
                    "公式：经营现金流 − 购建长期资产支付的现金。该值是代理指标，不是标准 FCFF。"));
        }

        BigDecimal growth = parameters.getConfirmedGrowth();
        if (growth == null) {
            assumptions.add(assumption("预测增速", "未确认", "default_unconfirmed", "营业收入同比不能代表 FCFF 增速；缺少经确认的现金流预测。"));
        } else {
            assumptions.add(assumption("预测增速", growth.toPlainString(), "user_confirmed", "用户确认的现金流预测增速；不是财报事实。"));
        }
        String parameterSource = parameters.isAssumptionsConfirmed() ? "user_confirmed" : "default_unconfirmed";
        assumptions.add(assumption("WACC", wacc.toPlainString(), parameterSource, "须确认无风险利率、风险溢价和资本结构依据。"));
        assumptions.add(assumption("永续增长率", terminalGrowth.toPlainString(), parameterSource, "不是财报事实，按确认输入计算。"));
        assumptions.add(assumption("显式预测期", String.valueOf(years), parameterSource, "按确认输入计算。"));

        // 逐项列示全部门禁，避免早退时隐藏后续缺失或未确认项。
        List<String> missing = new ArrayList<>();
        if (!parameters.isAssumptionsConfirmed()) missing.add("WACC、永续增长率及预测期未明确确认");
        if (growth == null) missing.add("预测增速未确认");
        if (parameters.getConfirmedFcff() == null) missing.add("标准 FCFF 未确认");
        if (!parameters.isBridgePolicyConfirmed() || parameters.getBridgePolicy() == null || parameters.getBridgePolicy().isBlank()) missing.add("现金、债务、少数股东及非核心资产桥接政策未确认");
        for (String field : List.of("cash", "restricted_cash", "short_term_debt", "current_portion_debt", "bonds_payable", "lease_liabilities", "long_term_debt")) {
            ExtractedLine line = byField.get(field);
            assumptions.add(assumption(field, line == null || line.getCurrent() == null ? "原文未找到" : line.getCurrent().toPlainString(), "extract",
                    line == null ? "缺少抽取证据；人工确认桥接口径时须提供依据" : "第 " + line.getPage() + " 页；" + line.getSnippet()));
        }
        if (parameters.getUsableCash() == null) missing.add("可用现金未确认");
        if (parameters.getGrossDebt() == null) missing.add("有息债务未确认");
        if (parameters.getMinorityAdjustment() == null) missing.add("少数股东调整未确认");
        if (parameters.getNonCoreAssets() == null) missing.add("非核心资产调整未确认");
        if (parameters.getDilutedShares() == null) missing.add("摊薄股数未确认");
        if (!missing.isEmpty()) {
            boolean bridgeInputMissing = parameters.getUsableCash() == null
                    || parameters.getGrossDebt() == null
                    || parameters.getMinorityAdjustment() == null
                    || parameters.getNonCoreAssets() == null;
            if (bridgeInputMissing) {
                assumptions.add(assumption("净债务诊断", diagnostic.getNetDebtDiagnostic() == null ? "输入不完整" : diagnostic.getNetDebtDiagnostic().toPlainString(), "formula",
                        diagnostic.getNetDebtDiagnosticFormula() + "；正式股权桥接另需确认少数股东和非核心资产政策。"));
            }
            return withDiagnostic(blocked("INPUT_INCOMPLETE", String.join("；", missing), assumptions), diagnostic);
        }
        if (parameters.getConfirmedFcff().signum() <= 0 || parameters.getDilutedShares().signum() <= 0
                || parameters.getUsableCash().signum() < 0 || parameters.getGrossDebt().signum() < 0
                || parameters.getMinorityAdjustment().signum() < 0 || parameters.getNonCoreAssets().signum() < 0
                || years > 30 || wacc.signum() <= 0 || terminalGrowth.compareTo(new BigDecimal("-1")) <= 0)
            return blocked("BLOCKED_BY_DATA_QUALITY", "确认的 FCFF、股数、桥接金额或预测参数超出模型边界", assumptions);
        assumptions.add(assumption("桥接政策", parameters.getBridgePolicy(), "user_confirmed", "确认版本 " + parameters.getVersion()));
        BigDecimal netDebt = parameters.getGrossDebt().subtract(parameters.getUsableCash());
        assumptions.add(assumption("可用现金", parameters.getUsableCash().toPlainString(), "user_confirmed", "现金范围按确认桥接政策"));
        assumptions.add(assumption("有息债务", parameters.getGrossDebt().toPlainString(), "user_confirmed", "债务范围按确认桥接政策"));
        assumptions.add(assumption("少数股东调整", parameters.getMinorityAdjustment().toPlainString(), "user_confirmed", "从企业价值扣减"));
        assumptions.add(assumption("非核心资产调整", parameters.getNonCoreAssets().toPlainString(), "user_confirmed", "加入普通股股权价值"));
        assumptions.add(assumption("摊薄股数", parameters.getDilutedShares().toPlainString(), "user_confirmed", "股数单位为股"));
        assumptions.add(assumption("净债务", netDebt.toPlainString(), "formula", "确认有息债务减确认可用现金，金额单位为元"));
        if (netDebt == null) {
            return blocked("INPUT_INCOMPLETE", "净债务桥接输入不完整。", assumptions);
        }
        if (growth == null) {
            return blocked("INPUT_INCOMPLETE", "缺少经确认的现金流预测增速。", assumptions);
        }
        if (growth.compareTo(G_CAP) > 0 || growth.compareTo(G_FLOOR) < 0) {
            assumptions.add(assumption("预测增速", "无效", "user_confirmed", "确认增速超出 -30% 至 30% 的模型边界。"));
            return blocked("BLOCKED_BY_DATA_QUALITY", "预测增速超过模型边界。", assumptions);
        }
        if (parameters.getConfirmedFcff() == null) {
            assumptions.add(assumption("标准 FCFF", "未确认", "default_unconfirmed",
                    "简式自由现金流未调整税后利息、营运资本和非现金项目，不能作为正式 DCF 基数。"));
            return blocked("INPUT_INCOMPLETE", "缺少经确认的标准 FCFF 预测，正式 DCF 不计算。", assumptions);
        }
        assumptions.add(assumption("标准 FCFF", parameters.getConfirmedFcff().toPlainString(), "user_confirmed",
                "用户确认的标准 FCFF 基期值；不是从简式自由现金流自动推导。"));
        BigDecimal fcff = parameters.getConfirmedFcff();
        BigDecimal shares = parameters.getDilutedShares();
        BigDecimal equityAdjustment = parameters.getMinorityAdjustment().subtract(parameters.getNonCoreAssets());
        ValuationResultDTO.DCFValuation.Scenario base = scenario("基准", fcff, growth, wacc, terminalGrowth, netDebt.add(equityAdjustment), years, shares);
        ValuationResultDTO.DCFValuation.Scenario optimistic = scenario("乐观", fcff, growth.add(new BigDecimal("0.02")), wacc, terminalGrowth, netDebt.add(equityAdjustment), years, shares);
        ValuationResultDTO.DCFValuation.Scenario pessimistic = scenario("悲观", fcff, growth.subtract(new BigDecimal("0.02")), wacc, terminalGrowth, netDebt.add(equityAdjustment), years, shares);

        boolean equity = base.getEquityValue() != null;
        BigDecimal terminalPv = discount(base.getTerminalValue(), wacc, years);
        BigDecimal terminalShare = base.getEnterpriseValue().compareTo(BigDecimal.ZERO) == 0
                ? null : terminalPv.divide(base.getEnterpriseValue(), 8, RoundingMode.HALF_UP);
        if (terminalShare != null) {
            assumptions.add(assumption("终值现值占比", terminalShare.toPlainString(), "formula",
                    terminalShare.compareTo(new BigDecimal("0.75")) > 0
                            ? "超过 75%，估值高度依赖永续增长率，建议延长显式预测期或采用三阶段模型。"
                            : "终值现值占企业价值比例，作为模型稳定性检查。"));
        }
        BigDecimal low = min(valueOf(pessimistic, equity), valueOf(base, equity), valueOf(optimistic, equity));
        BigDecimal high = max(valueOf(pessimistic, equity), valueOf(base, equity), valueOf(optimistic, equity));
        String basis = equity ? "股权价值" : "企业价值";
        return ValuationResultDTO.builder()
                .businessStatus("COMPUTED")
                .dcfValuation(ValuationResultDTO.DCFValuation.builder()
                        .baseCase(base)
                        .optimistic(optimistic)
                        .pessimistic(pessimistic)
                        .wacc(wacc)
                        .terminalGrowthRate(terminalGrowth)
                        .build())
                .sensitivityAnalysis(sensitivity(fcff, growth, netDebt.add(equityAdjustment), shares, years, wacc, terminalGrowth))
                .assumptions(assumptions)
                .valuationRange(ValuationResultDTO.ValuationRange.builder()
                        .low(low)
                        .mid(valueOf(base, equity))
                        .high(high)
                        .methodology("推论：" + years + "年 FCFF 折现加戈登终值。区间取悲观/基准/乐观增速（基准增速 ±2 个百分点）的" + basis + "。")
                        .applicabilityNote("按确认政策扣净债务及少数股东调整、加非核心资产，以确认摊薄股数计算每股价值。相对估值因没有可比公司和市价未计算。不构成投资建议。")
                        .build())
                .netDebtDiagnostic(diagnostic.getNetDebtDiagnostic()).netDebtDiagnosticStatus(diagnostic.getNetDebtDiagnosticStatus())
                .netDebtDiagnosticMissingItems(diagnostic.getNetDebtDiagnosticMissingItems())
                .netDebtDiagnosticFormula(diagnostic.getNetDebtDiagnosticFormula()).netDebtDiagnosticInputs(diagnostic.getNetDebtDiagnosticInputs()).build();
    }

    private ValuationResultDTO blocked(String status, String reason, List<ValuationResultDTO.ValuationAssumption> assumptions) {
        assumptions.add(assumption("适用边界", "未计算", "formula", reason));
        return ValuationResultDTO.builder().businessStatus(status).blockReason(reason).assumptions(assumptions).build();
    }

    private ValuationResultDTO withDiagnostic(ValuationResultDTO result, ValuationResultDTO diagnostic) {
        result.setNetDebtDiagnostic(diagnostic.getNetDebtDiagnostic());
        result.setNetDebtDiagnosticStatus(diagnostic.getNetDebtDiagnosticStatus());
        result.setNetDebtDiagnosticMissingItems(diagnostic.getNetDebtDiagnosticMissingItems());
        result.setNetDebtDiagnosticFormula(diagnostic.getNetDebtDiagnosticFormula());
        result.setNetDebtDiagnosticInputs(diagnostic.getNetDebtDiagnosticInputs());
        return result;
    }

    private ValuationResultDTO.DCFValuation.Scenario scenario(String name, BigDecimal fcff0, BigDecimal growth,
                                                              BigDecimal wacc, BigDecimal terminalGrowth, BigDecimal netDebt,
                                                              int years, BigDecimal shares) {
        List<BigDecimal> flows = new ArrayList<>();
        BigDecimal present = BigDecimal.ZERO;
        for (int year = 1; year <= years; year++) {
            BigDecimal flow = fcff0.multiply(BigDecimal.ONE.add(growth).pow(year)).setScale(4, RoundingMode.HALF_UP);
            flows.add(flow);
            present = present.add(discount(flow, wacc, year));
        }
        BigDecimal last = flows.get(years - 1);
        BigDecimal terminal = last.multiply(BigDecimal.ONE.add(terminalGrowth))
                .divide(wacc.subtract(terminalGrowth), 4, RoundingMode.HALF_UP);
        BigDecimal enterprise = present.add(discount(terminal, wacc, years)).setScale(4, RoundingMode.HALF_UP);
        BigDecimal equity = netDebt == null ? null : enterprise.subtract(netDebt).setScale(4, RoundingMode.HALF_UP);
        BigDecimal perShare = shares == null || shares.compareTo(BigDecimal.ZERO) <= 0 || equity == null
                ? null : equity.divide(shares, 4, RoundingMode.HALF_UP);
        return ValuationResultDTO.DCFValuation.Scenario.builder()
                .name(name)
                .enterpriseValue(enterprise)
                .equityValue(equity).valuePerShare(perShare)
                .freeCashFlows(flows)
                .terminalValue(terminal)
                .build();
    }

    private ValuationResultDTO.SensitivityAnalysis sensitivity(BigDecimal fcff0, BigDecimal growth, BigDecimal netDebt, BigDecimal shares, int years, BigDecimal baseWacc, BigDecimal baseTerminal) {
        List<BigDecimal> offsets = List.of(new BigDecimal("-0.02"), new BigDecimal("-0.01"), BigDecimal.ZERO, new BigDecimal("0.01"), new BigDecimal("0.02"));
        List<BigDecimal> waccRange = offsets.stream().map(baseWacc::add).toList();
        List<BigDecimal> terminalRange = offsets.stream().map(baseTerminal::add).toList();
        List<List<BigDecimal>> matrix = new ArrayList<>();
        for (BigDecimal wacc : waccRange) {
            List<BigDecimal> row = new ArrayList<>();
            for (BigDecimal terminal : terminalRange) {
                if (terminal.compareTo(wacc) >= 0 || wacc.signum() <= 0 || terminal.compareTo(new BigDecimal("-1")) <= 0) {
                    row.add(null);
                } else {
                    ValuationResultDTO.DCFValuation.Scenario cell = scenario("敏感性", fcff0, growth, wacc, terminal, netDebt, years, shares);
                    row.add(netDebt == null ? cell.getEnterpriseValue() : cell.getEquityValue());
                }
            }
            matrix.add(row);
        }
        return ValuationResultDTO.SensitivityAnalysis.builder()
                .waccRange(waccRange)
                .terminalGrowthRange(terminalRange)
                .valuationMatrix(matrix)
                .build();
    }

    private static BigDecimal discount(BigDecimal amount, BigDecimal rate, int year) {
        return amount.divide(BigDecimal.ONE.add(rate).pow(year), 8, RoundingMode.HALF_UP);
    }

    private static BigDecimal valueOf(ValuationResultDTO.DCFValuation.Scenario scenario, boolean equity) {
        return equity ? scenario.getEquityValue() : scenario.getEnterpriseValue();
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b, BigDecimal c) {
        return a.min(b).min(c);
    }

    private static BigDecimal max(BigDecimal a, BigDecimal b, BigDecimal c) {
        return a.max(b).max(c);
    }

    private static BigDecimal current(Map<String, ExtractedLine> byField, String fieldId) {
        ExtractedLine line = byField.get(fieldId);
        return line == null ? null : line.getCurrent();
    }

    private static boolean sameAnnualConsolidated(ExtractedLine left, ExtractedLine right) {
        return left != null && right != null && left.getUnit() != null && left.getUnit().equals(right.getUnit())
                && "consolidated".equals(left.getScope()) && left.getScope().equals(right.getScope())
                && "annual".equals(left.getPeriodType()) && left.getPeriodType().equals(right.getPeriodType());
    }

    private static ValuationResultDTO.ValuationAssumption assumption(String parameter, String value, String source, String rationale) {
        return ValuationResultDTO.ValuationAssumption.builder()
                .parameter(parameter)
                .value(value)
                .source(source)
                .rationale(rationale)
                .confidence(0.35)
                .build();
    }
}

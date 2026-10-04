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
        Map<String, ExtractedLine> byField = lines.stream()
                .collect(Collectors.toMap(ExtractedLine::getFieldId, Function.identity(), (a, b) -> a));
        List<ValuationResultDTO.ValuationAssumption> assumptions = new ArrayList<>();
        FinancialMetricsDTO.CoreMetrics core = metrics.getCoreMetrics();
        BigDecimal ocf = core == null ? null : core.getOperatingCashFlow();
        if (ocf == null) {
            assumptions.add(assumption("简式自由现金流", "未计算", "formula", "缺少同年度经营活动现金流量净额。"));
            return blocked("INPUT_INCOMPLETE", "缺少简式自由现金流基础字段。", assumptions);
        }
        BigDecimal capex = current(byField, "capex");
        if (capex == null || !sameAnnualConsolidated(byField.get("operating_cash_flow"), byField.get("capex"))) {
            assumptions.add(assumption("简式自由现金流", "未计算", "formula", "缺少同年度、同范围、同单位资本开支。"));
            return blocked("BLOCKED_BY_DATA_QUALITY", "经营现金流和资本开支口径不一致。", assumptions);
        }
        BigDecimal simpleFcf = ocf.subtract(capex);
        assumptions.add(assumption("简式自由现金流", simpleFcf.toPlainString(), "formula",
                "公式：经营现金流 − 购建长期资产支付的现金。该值是代理指标，不是标准 FCFF。"));

        BigDecimal growth = confirmedGrowth;
        if (growth == null) {
            assumptions.add(assumption("预测增速", "未确认", "default_unconfirmed", "营业收入同比不能代表 FCFF 增速；缺少经确认的现金流预测。"));
        } else {
            assumptions.add(assumption("预测增速", growth.toPlainString(), "user_confirmed", "用户确认的现金流预测增速；不是财报事实。"));
        }
        assumptions.add(assumption("WACC", WACC.toPlainString(), "default_unconfirmed", "缺少经确认的无风险利率、风险溢价和资本结构。"));
        assumptions.add(assumption("永续增长率", TERMINAL_G.toPlainString(), "default_unconfirmed", "缺省 2%，不是公司指引。"));
        assumptions.add(assumption("显式预测期", String.valueOf(YEARS), "default_unconfirmed", "缺省五年，尚未确认。"));

        BigDecimal netDebt = netDebt(byField, assumptions);
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
        if (confirmedFcff == null) {
            assumptions.add(assumption("标准 FCFF", "未确认", "default_unconfirmed",
                    "简式自由现金流未调整税后利息、营运资本和非现金项目，不能作为正式 DCF 基数。"));
            return blocked("INPUT_INCOMPLETE", "缺少经确认的标准 FCFF 预测，正式 DCF 不计算。", assumptions);
        }
        assumptions.add(assumption("标准 FCFF", confirmedFcff.toPlainString(), "user_confirmed",
                "用户确认的标准 FCFF 基期值；不是从简式自由现金流自动推导。"));
        BigDecimal fcff = confirmedFcff;
        ValuationResultDTO.DCFValuation.Scenario base = scenario("基准", fcff, growth, WACC, TERMINAL_G, netDebt);
        ValuationResultDTO.DCFValuation.Scenario optimistic = scenario("乐观", fcff, growth.add(new BigDecimal("0.02")), WACC, TERMINAL_G, netDebt);
        ValuationResultDTO.DCFValuation.Scenario pessimistic = scenario("悲观", fcff, growth.subtract(new BigDecimal("0.02")), WACC, TERMINAL_G, netDebt);

        boolean equity = base.getEquityValue() != null;
        BigDecimal terminalPv = discount(base.getTerminalValue(), WACC, YEARS);
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
                        .wacc(WACC)
                        .terminalGrowthRate(TERMINAL_G)
                        .build())
                .sensitivityAnalysis(sensitivity(fcff, growth, netDebt))
                .assumptions(assumptions)
                .valuationRange(ValuationResultDTO.ValuationRange.builder()
                        .low(low)
                        .mid(valueOf(base, equity))
                        .high(high)
                        .methodology("推论：五年 FCFF 折现加戈登终值。区间取悲观/基准/乐观增速（基准增速 ±2 个百分点）的" + basis + "。")
                        .applicabilityNote("只在假设表成立时适用。未扣少数股东权益，未加非核心资产，未做摊薄。相对估值因没有可比公司和市价，未计算。不构成投资建议。")
                        .build())
                .build();
    }

    private ValuationResultDTO blocked(String status, String reason, List<ValuationResultDTO.ValuationAssumption> assumptions) {
        assumptions.add(assumption("适用边界", "未计算", "formula", reason));
        return ValuationResultDTO.builder().businessStatus(status).blockReason(reason).assumptions(assumptions).build();
    }

    private BigDecimal netDebt(Map<String, ExtractedLine> byField, List<ValuationResultDTO.ValuationAssumption> assumptions) {
        ExtractedLine cash = byField.get("cash");
        ExtractedLine restrictedCash = byField.get("restricted_cash");
        List<String> debtFields = List.of("short_term_debt", "current_portion_debt", "bonds_payable", "lease_liabilities", "long_term_debt");
        List<ExtractedLine> debtLines = debtFields.stream().map(byField::get).toList();
        if (!usableBalanceSheet(cash) || !usableBalanceSheet(restrictedCash)
                || debtLines.stream().anyMatch(line -> !usableBalanceSheet(line))
                || debtLines.stream().anyMatch(line -> !cash.getUnit().equals(line.getUnit()))) {
            assumptions.add(assumption("净债务", "未计算（桥接输入不完整）", "formula",
                    "需齐备货币资金、受限资金、短期借款、一年内到期债务、应付债券、租赁负债和长期借款。"));
            return null;
        }
        BigDecimal grossDebt = debtLines.stream().map(ExtractedLine::getCurrent).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netDebt = grossDebt.subtract(cash.getCurrent()).add(restrictedCash.getCurrent());
        assumptions.add(assumption("净债务", netDebt.toPlainString(), "formula",
                "公式：全部有息债务 − 货币资金 + 受限资金。财务公司资金按已确认的受限资金口径处理。"));
        return netDebt;
    }

    private ValuationResultDTO.DCFValuation.Scenario scenario(String name, BigDecimal fcff0, BigDecimal growth,
                                                              BigDecimal wacc, BigDecimal terminalGrowth, BigDecimal netDebt) {
        List<BigDecimal> flows = new ArrayList<>();
        BigDecimal present = BigDecimal.ZERO;
        for (int year = 1; year <= YEARS; year++) {
            BigDecimal flow = fcff0.multiply(BigDecimal.ONE.add(growth).pow(year)).setScale(4, RoundingMode.HALF_UP);
            flows.add(flow);
            present = present.add(discount(flow, wacc, year));
        }
        BigDecimal last = flows.get(YEARS - 1);
        BigDecimal terminal = last.multiply(BigDecimal.ONE.add(terminalGrowth))
                .divide(wacc.subtract(terminalGrowth), 4, RoundingMode.HALF_UP);
        BigDecimal enterprise = present.add(discount(terminal, wacc, YEARS)).setScale(4, RoundingMode.HALF_UP);
        BigDecimal equity = netDebt == null ? null : enterprise.subtract(netDebt).setScale(4, RoundingMode.HALF_UP);
        return ValuationResultDTO.DCFValuation.Scenario.builder()
                .name(name)
                .enterpriseValue(enterprise)
                .equityValue(equity)
                .freeCashFlows(flows)
                .terminalValue(terminal)
                .build();
    }

    private ValuationResultDTO.SensitivityAnalysis sensitivity(BigDecimal fcff0, BigDecimal growth, BigDecimal netDebt) {
        List<BigDecimal> waccRange = List.of(
                new BigDecimal("0.07"), new BigDecimal("0.08"), new BigDecimal("0.09"),
                new BigDecimal("0.10"), new BigDecimal("0.11"));
        List<BigDecimal> terminalRange = List.of(
                new BigDecimal("0.00"), new BigDecimal("0.01"), new BigDecimal("0.02"),
                new BigDecimal("0.03"), new BigDecimal("0.04"));
        List<List<BigDecimal>> matrix = new ArrayList<>();
        for (BigDecimal wacc : waccRange) {
            List<BigDecimal> row = new ArrayList<>();
            for (BigDecimal terminal : terminalRange) {
                if (terminal.compareTo(wacc) >= 0) {
                    row.add(null);
                } else {
                    ValuationResultDTO.DCFValuation.Scenario cell = scenario("敏感性", fcff0, growth, wacc, terminal, netDebt);
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

    private static boolean usableBalanceSheet(ExtractedLine line) {
        return line != null && line.getCurrent() != null && "consolidated".equals(line.getScope())
                && "point_in_time".equals(line.getPeriodType()) && line.getUnit() != null;
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

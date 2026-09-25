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
        Map<String, ExtractedLine> byField = lines.stream()
                .collect(Collectors.toMap(ExtractedLine::getFieldId, Function.identity(), (a, b) -> a));
        List<ValuationResultDTO.ValuationAssumption> assumptions = new ArrayList<>();
        FinancialMetricsDTO.CoreMetrics core = metrics.getCoreMetrics();
        BigDecimal ocf = core == null ? null : core.getOperatingCashFlow();
        if (ocf == null) {
            assumptions.add(assumption("FCFF", "未提取", "缺少经营活动现金流量净额，DCF 不计算"));
            return blocked(assumptions);
        }
        BigDecimal capex = current(byField, "capex");
        BigDecimal fcff;
        if (capex != null && sameUnit(byField.get("operating_cash_flow"), byField.get("capex"))) {
            fcff = ocf.subtract(capex);
            assumptions.add(assumption("FCFF", fcff.toPlainString(), "公式：经营现金流 − 购建长期资产支付的现金。资本开支取支出额，未加回处置收入。"));
        } else {
            fcff = ocf;
            assumptions.add(assumption("FCFF", fcff.toPlainString(), "未提取资本开支。FCFF 暂用经营现金流代替，没有扣资本开支，企业价值会偏高。这是假设，不是财报科目。"));
        }

        BigDecimal historical = metrics.getYoyGrowth() == null ? null : metrics.getYoyGrowth().getRevenueGrowth();
        String sourceName = "营业收入同比";
        if (historical == null && metrics.getYoyGrowth() != null) {
            historical = metrics.getYoyGrowth().getNetProfitGrowth();
            sourceName = "归母净利润同比";
        }
        if (historical == null) {
            assumptions.add(assumption("预测增速", "未提取", "没有两期收入或利润，不做现金流外推"));
            return blocked(assumptions);
        }
        BigDecimal growth = historical;
        if (historical.compareTo(G_CAP) > 0) {
            growth = G_CAP;
            assumptions.add(assumption("预测增速", growth.toPlainString(),
                    sourceName + "为 " + historical.toPlainString() + "，预测期封顶 30%。封顶是模型假设。"));
        } else if (historical.compareTo(G_FLOOR) < 0) {
            growth = G_FLOOR;
            assumptions.add(assumption("预测增速", growth.toPlainString(),
                    sourceName + "为 " + historical.toPlainString() + "，预测期封底 -30%。封底是模型假设。"));
        } else {
            assumptions.add(assumption("预测增速", growth.toPlainString(), "直接采用" + sourceName + "，没有行业或管理层指引调整。"));
        }
        assumptions.add(assumption("WACC", WACC.toPlainString(), "没有无风险利率、股权风险溢价和资本结构。折现率取 9%，是假设不是事实。"));
        assumptions.add(assumption("永续增长率", TERMINAL_G.toPlainString(), "缺省 2%，并且必须低于 WACC。不是公司指引。"));
        assumptions.add(assumption("显式预测期", String.valueOf(YEARS), "固定 5 年。"));

        BigDecimal netDebt = netDebt(byField, assumptions);
        ValuationResultDTO.DCFValuation.Scenario base = scenario("基准", fcff, growth, WACC, TERMINAL_G, netDebt);
        ValuationResultDTO.DCFValuation.Scenario optimistic = scenario("乐观", fcff, growth.add(new BigDecimal("0.02")), WACC, TERMINAL_G, netDebt);
        ValuationResultDTO.DCFValuation.Scenario pessimistic = scenario("悲观", fcff, growth.subtract(new BigDecimal("0.02")), WACC, TERMINAL_G, netDebt);

        boolean equity = base.getEquityValue() != null;
        BigDecimal low = min(valueOf(pessimistic, equity), valueOf(base, equity), valueOf(optimistic, equity));
        BigDecimal high = max(valueOf(pessimistic, equity), valueOf(base, equity), valueOf(optimistic, equity));
        String basis = equity ? "股权价值" : "企业价值";
        return ValuationResultDTO.builder()
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

    private ValuationResultDTO blocked(List<ValuationResultDTO.ValuationAssumption> assumptions) {
        assumptions.add(assumption("适用边界", "未计算", "关键输入缺失，不输出估值区间，避免把假设填成结果。"));
        return ValuationResultDTO.builder().assumptions(assumptions).build();
    }

    private BigDecimal netDebt(Map<String, ExtractedLine> byField, List<ValuationResultDTO.ValuationAssumption> assumptions) {
        ExtractedLine cash = byField.get("cash");
        ExtractedLine shortDebt = byField.get("short_term_debt");
        ExtractedLine longDebt = byField.get("long_term_debt");
        if (cash == null || shortDebt == null || longDebt == null
                || cash.getCurrent() == null || shortDebt.getCurrent() == null || longDebt.getCurrent() == null
                || !cash.getUnit().equals(shortDebt.getUnit()) || !cash.getUnit().equals(longDebt.getUnit())) {
            assumptions.add(assumption("净债务", "未提取", "货币资金、短期借款、长期借款未齐套或单位不一致。不桥接到股权价值，区间使用企业价值。"));
            return null;
        }
        BigDecimal netDebt = shortDebt.getCurrent().add(longDebt.getCurrent()).subtract(cash.getCurrent());
        assumptions.add(assumption("净债务", netDebt.toPlainString(),
                "公式：短期借款 + 长期借款 − 货币资金。不含债券、租赁负债和受限资金，股权价值会偏离。"));
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

    private static boolean sameUnit(ExtractedLine left, ExtractedLine right) {
        return left != null && right != null && left.getUnit() != null && left.getUnit().equals(right.getUnit());
    }

    private static ValuationResultDTO.ValuationAssumption assumption(String parameter, String value, String rationale) {
        return ValuationResultDTO.ValuationAssumption.builder()
                .parameter(parameter)
                .value(value)
                .source("formula")
                .rationale(rationale)
                .confidence(0.35)
                .build();
    }
}

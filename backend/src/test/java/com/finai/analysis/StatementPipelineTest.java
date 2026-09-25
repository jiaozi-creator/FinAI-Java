package com.finai.analysis;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class StatementPipelineTest {

    private final StatementExtractor extractor = new StatementExtractor();
    private final MetricCalculator calculator = new MetricCalculator();
    private final AnomalyRuleEngine anomalies = new AnomalyRuleEngine();
    private final ValuationEngine valuation = new ValuationEngine();

    @Test
    void extractsYoYAnomaliesAndDcfFromStatementText() {
        String text = """
                单位：万元
                合并利润表
                营业收入 10,000 8,000
                营业成本 6,000 5,000
                归属于母公司股东的净利润 1,500 1,000
                扣除非经常性损益后的净利润 900 950
                合并现金流量表
                经营活动产生的现金流量净额 400 1,200
                合并资产负债表
                资产总计 20,000 18,000
                负债合计 8,000 7,000
                归属于母公司所有者权益合计 12,000 11,000
                货币资金 1,000 900
                短期借款 500 400
                长期借款 2,000 1,800
                会计政策变更：本公司追溯调整了收入确认时点。
                """;

        StatementExtract extract = extractor.extractPages(List.of(new PageText(12, text)), "sample.txt");
        var metrics = calculator.calculate(extract.getLines(), "2024A");
        var checks = calculator.articulate(extract.getLines());
        var signals = anomalies.detect(extract.getLines(), metrics, extract.getPolicyNotes());
        var value = valuation.evaluate(extract.getLines(), metrics);

        assertThat(metrics.getCoreMetrics().getRevenue()).isEqualByComparingTo("10000");
        assertThat(metrics.getYoyGrowth().getRevenueGrowth()).isCloseTo(new BigDecimal("0.25"), within(new BigDecimal("0.0001")));
        assertThat(metrics.getRatios().getGrossMargin()).isCloseTo(new BigDecimal("0.4"), within(new BigDecimal("0.0001")));
        assertThat(metrics.getQoqGrowth()).isNull();
        assertThat(checks.get(0).getStatus()).isEqualTo("PASS");
        assertThat(signals).extracting(signal -> signal.getType().name())
                .contains("NON_RECURRING_ITEMS", "PROFIT_CASHFLOW_DIVERGENCE", "ACCOUNTING_POLICY_CHANGE");
        assertThat(value.getValuationRange()).isNotNull();
        assertThat(value.getValuationRange().getMid()).isNotNull();
        assertThat(value.getAssumptions()).anyMatch(item -> "WACC".equals(item.getParameter()) && "formula".equals(item.getSource()));
        assertThat(value.getRelativeValuation()).isNull();
    }

    @Test
    void addsMinorityInterestAndNonRecurringLines() {
        String text = """
                单位：元
                合并资产负债表
                资产总计 1000 900
                负债合计 400 300
                归属于母公司所有者权益合计 500 520
                少数股东权益 100 80
                期末总股本 1,250,081,601.00 1,252,270,215.00 -0.17
                基本每股收益（元／股） 35.57 36.18 -1.68
                非经常性损益项目和金额
                非流动性资产处置损益，包括已计提资产减
                -1,864,170.69
                值准备的冲销部分
                计入当期损益的政府补助，但与公司正常经
                20,012,654.88
                营业务密切相关
                合计 52,672,775.85
                十、存在股权激励
                """;
        StatementExtract extract = extractor.extractPages(List.of(new PageText(5, text)), "nri.txt");
        var metrics = calculator.calculate(extract.getLines(), "2026H1");
        var checks = calculator.articulate(extract.getLines());
        assertThat(metrics.getCoreMetrics().getMinorityInterest()).isEqualByComparingTo("100");
        assertThat(metrics.getCoreMetrics().getSharesOutstanding()).isEqualByComparingTo("1250081601.00");
        assertThat(metrics.getCoreMetrics().getBasicEps()).isEqualByComparingTo("35.57");
        assertThat(checks.get(0).getStatus()).isEqualTo("PASS");
        assertThat(checks.get(0).getName()).contains("少数股东权益");
        assertThat(metrics.getNonRecurringItems()).extracting(item -> item.getFieldId())
                .contains("nri_disposal", "nri_subsidy", "nri_total");
        assertThat(metrics.getNonRecurringItems()).anyMatch(item ->
                "nri_total".equals(item.getFieldId()) && item.getAmount().compareTo(new BigDecimal("52672775.85")) == 0);
    }

    @Test
    void skipsValuationWhenCashFlowMissing() {
        String text = """
                单位：元
                合并利润表
                营业收入 100 80
                """;
        StatementExtract extract = extractor.extractPages(List.of(new PageText(1, text)), "partial.txt");
        var metrics = calculator.calculate(extract.getLines(), "2024A");
        var value = valuation.evaluate(extract.getLines(), metrics);
        assertThat(value.getValuationRange()).isNull();
        assertThat(value.getAssumptions()).anyMatch(item -> item.getValue().equals("未提取"));
    }
}

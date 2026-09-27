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
    void prefersConsolidatedStatementOverParentSummaryAndNotes() {
        String text = """
                单位：元
                主要会计数据
                归属于上市公司股
                101,035,276
                东的扣除非经常性 -39,312,271.78 -279,702,121.97 85.94
                .19
                损益的净利润
                合并资产负债表
                货币资金 1 1,088,855,303.96 1,006,282,974.26
                存货 10 1,781,225,167.08 1,643,949,298.23
                资产总计 9,627,422,735.04 9,622,769,572.52
                负债合计 6,086,960,046.31 5,936,747,650.29
                归属于母公司所有者权益
                3,499,230,196.18 3,628,152,636.36
                （或股东权益）合计
                少数股东权益 41,232,492.55 57,869,285.87
                母公司资产负债表
                货币资金 604,196,292.37 460,873,222.05
                存货 317,311,184.44 320,547,801.76
                合并利润表
                其中：营业收入 61 8,912,075,395.97 8,449,386,211.83
                五、净利润（净亏损以“－”号填列） -20,489,710.90 -178,963,052.55
                1.归属于母公司股东的净利润
                （净亏损以“-”号填列）
                -9,822,497.95 -151,420,324.40
                （一）基本每股收益(元/股) -0.02 -0.27
                母公司利润表
                四、净利润（净亏损以“－”号填列） 251,815,440.50 526,783,089.28
                合并现金流量表
                经营活动产生的现金流量净额 883,073,254.93 963,903,027.07
                购买日公允价值 购买日账面价值
                货币资金 2,839,279.97 2,839,279.97
                存货 3,385,276.10 3,385,276.10
                减：少数股东权益 -725,835.34 -725,835.34
                """;

        StatementExtract extract = extractor.extractPages(List.of(new PageText(93, text)), "603313-layout.txt");
        var byId = extract.getLines().stream().collect(java.util.stream.Collectors.toMap(
                ExtractedLine::getFieldId, line -> line, (a, b) -> a));

        assertThat(byId.get("net_profit").getCurrent()).isEqualByComparingTo("-9822497.95");
        assertThat(byId.get("net_profit").getPrior()).isEqualByComparingTo("-151420324.40");
        assertThat(byId.get("net_profit").getScope()).isEqualTo("consolidated");
        assertThat(byId.get("net_profit_deducted").getCurrent()).isEqualByComparingTo("-39312271.78");
        assertThat(byId.get("net_profit_deducted").getPrior()).isEqualByComparingTo("-279702121.97");
        assertThat(byId.get("cash").getCurrent()).isEqualByComparingTo("1088855303.96");
        assertThat(byId.get("cash").getScope()).isEqualTo("consolidated");
        assertThat(byId.get("inventory").getCurrent()).isEqualByComparingTo("1781225167.08");
        assertThat(byId.get("minority_interest").getCurrent()).isEqualByComparingTo("41232492.55");
        assertThat(byId.get("minority_interest").getScope()).isEqualTo("consolidated");
        assertThat(byId.get("net_assets").getCurrent()).isEqualByComparingTo("3499230196.18");
        assertThat(byId.get("net_assets").getScope()).isEqualTo("consolidated");
        assertThat(byId.get("basic_eps").getCurrent()).isEqualByComparingTo("-0.02");
        assertThat(byId.get("revenue").getCurrent()).isEqualByComparingTo("8912075395.97");
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

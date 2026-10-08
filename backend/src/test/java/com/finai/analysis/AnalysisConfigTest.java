package com.finai.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisConfigTest {

    @Test
    void loadsThresholdsFieldsAndFormulasFromYaml() {
        AnalysisConfig config = AnalysisConfig.get();
        assertThat(config.ruleVersion()).isEqualTo("2026.1");
        assertThat(config.nonRecurringThreshold()).isEqualByComparingTo("0.20");
        assertThat(config.nonRecurringHighThreshold()).isEqualByComparingTo("0.50");
        assertThat(config.cashCoverageThreshold()).isEqualByComparingTo("0.50");
        assertThat(config.formulaExpression("roe")).isEqualTo("net_profit / net_assets");
        assertThat(config.formulaExpression("qoq_growth")).contains("不计算");
        assertThat(config.fields()).extracting(FieldSpec::fieldId).contains("revenue", "operating_cash_flow", "capex");
    }
}

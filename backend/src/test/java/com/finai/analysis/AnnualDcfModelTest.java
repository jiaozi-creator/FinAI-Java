package com.finai.analysis;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class AnnualDcfModelTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private ValuationParameters synthetic() {
        return ValuationParameters.builder().version("SYNTHETIC-1").inputMode("COMPONENTS").years(2)
                .wacc(d("0.10")).terminalGrowth(d("0.02")).assumptionsConfirmed(true)
                .valuationScope("WHOLE_GROUP").financialCompanyTreatment("合成样本，无财务公司")
                .forecastSource("合成测试，不代表任何公司预测")
                .inputDefinitions(Map.of("ebit", "经营利润调整后", "taxRate", "预测税率", "depreciation", "EBIT已扣折旧", "capex", "经营资本开支", "workingCapitalIncrease", "排除现金及债务"))
                .forecast(List.of(row(1, "100"), row(2, "120"))).build();
    }
    private ValuationParameters.ForecastYear row(int year, String ebit) {
        return ValuationParameters.ForecastYear.builder().year(year).ebit(d(ebit)).taxRate(d("0.25"))
                .depreciation(d("10")).capex(d("20")).workingCapitalIncrease(d("5")).source("合成测试").build();
    }
    @Test void computesSyntheticAnnualCashFlowsAndEnterpriseValueWithoutBridge() {
        var result = AnnualDcfModel.evaluate(synthetic(), null);
        var base = result.getDcfValuation().getBaseCase();
        assertThat(base.getFreeCashFlows()).containsExactly(d("60.00"), d("75.00"));
        // 独立答案：60/1.1 + 75/1.1² + (75*1.02/.08)/1.1²。
        assertThat(base.getEnterpriseValue()).isEqualByComparingTo("906.81818182");
        assertThat(base.getEquityValue()).isNull();
        assertThat(result.getBusinessStatus()).isEqualTo("EV_COMPUTED_BRIDGE_PENDING");
        assertThat(result.getSensitivityAnalysis().getValuationMatrix().get(2).get(2)).isEqualByComparingTo(base.getEnterpriseValue());
    }
    @Test void confirmedBridgeProducesEquityAndPerShare() {
        var p = synthetic().toBuilder().bridgePolicyConfirmed(true).bridgePolicy("合成测试确认范围")
                .usableCash(d("40")).grossDebt(d("20")).minorityAdjustment(d("5")).nonCoreAssets(d("3")).dilutedShares(d("10"))
                .bridgeSources(Map.of("usableCash", "合成现金", "grossDebt", "合成债务", "minorityAdjustment", "合成少数股东", "nonCoreAssets", "合成非核心资产", "dilutedShares", "合成股数")).build();
        var result = AnnualDcfModel.evaluate(p, null);
        assertThat(result.getNetDebtDiagnostic()).isEqualByComparingTo("-20");
        assertThat(result.getDcfValuation().getBaseCase().getEquityValue()).isEqualByComparingTo("924.81818182");
        assertThat(result.getDcfValuation().getBaseCase().getValuePerShare()).isEqualByComparingTo("92.48181818");
    }
    @Test void blocksUnconfirmedAndInvalidInputsAndLeavesInvalidSensitivityEmpty() {
        assertThat(AnnualDcfModel.evaluate(synthetic().toBuilder().assumptionsConfirmed(false).build(), null).getDcfValuation()).isNull();
        assertThat(AnnualDcfModel.inputErrors(synthetic().toBuilder().years(31).build())).isNotEmpty();
        var result = AnnualDcfModel.evaluate(synthetic().toBuilder().wacc(d("0.03")).build(), null);
        assertThat(result.getSensitivityAnalysis().getValuationMatrix().get(0).get(4)).isNull();
    }
}

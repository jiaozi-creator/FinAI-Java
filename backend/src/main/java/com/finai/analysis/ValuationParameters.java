package com.finai.analysis;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/** Explicit valuation inputs. Defaults are marked unconfirmed by the engine. */
@Value
@Builder(toBuilder = true)
@lombok.extern.jackson.Jacksonized
public class ValuationParameters {
    @Builder.Default BigDecimal wacc = new BigDecimal("0.09");
    @Builder.Default BigDecimal terminalGrowth = new BigDecimal("0.02");
    @Builder.Default int years = 5;
    BigDecimal confirmedGrowth;
    BigDecimal confirmedFcff;
    boolean assumptionsConfirmed;
    boolean bridgePolicyConfirmed;
    String bridgePolicy;
    BigDecimal usableCash;
    BigDecimal grossDebt;
    BigDecimal minorityAdjustment;
    BigDecimal nonCoreAssets;
    BigDecimal dilutedShares;
    String version;
    String inputMode;
    String valuationScope;
    String financialCompanyTreatment;
    String forecastSource;
    java.util.List<ForecastYear> forecast;
    java.util.Map<String, java.util.List<ForecastYear>> scenarios;
    java.util.Map<String, String> inputDefinitions;
    java.util.Map<String, String> bridgeSources;
    BigDecimal peMultiple;
    BigDecimal pbMultiple;
    String multipleSource;

    @Value
    @Builder
    @lombok.extern.jackson.Jacksonized
    public static class ForecastYear {
        int year;
        BigDecimal fcff;
        BigDecimal ebit;
        BigDecimal taxRate;
        BigDecimal depreciation;
        BigDecimal capex;
        BigDecimal workingCapitalIncrease;
        String source;

        public BigDecimal cashFlow(String mode) {
            if ("DIRECT".equals(mode)) return fcff;
            if (ebit == null || taxRate == null || depreciation == null || capex == null || workingCapitalIncrease == null) return null;
            return ebit.multiply(BigDecimal.ONE.subtract(taxRate)).add(depreciation).subtract(capex).subtract(workingCapitalIncrease);
        }
    }
}

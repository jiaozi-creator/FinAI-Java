package com.finai.analysis;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/** Explicit valuation inputs. Defaults are marked unconfirmed by the engine. */
@Value
@Builder
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
}

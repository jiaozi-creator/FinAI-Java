package com.finai.analysis;

import com.finai.model.dto.ValuationResultDTO;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class NetDebtDiagnosticsTest {
    private Map<String, ExtractedLine> inputs() {
        Map<String, ExtractedLine> fields = new LinkedHashMap<>();
        fields.put("cash", line("cash", "1000")); fields.put("restricted_cash", line("restricted_cash", "100"));
        for (String id : List.of("short_term_debt", "current_portion_debt", "bonds_payable", "lease_liabilities", "long_term_debt")) fields.put(id, line(id, "0"));
        return fields;
    }
    private ExtractedLine line(String id, String amount) {
        return ExtractedLine.builder().fieldId(id).fieldName(id).current(new BigDecimal(amount)).unit("元")
                .scope("consolidated").periodType("point_in_time").asOfDate("2023-12-31")
                .sourceFile("synthetic.txt").snippet("本期 上期 " + id + " " + amount).page(1).dataQualityStatus("VALUE_AVAILABLE").build();
    }
    private ValuationResultDTO evaluate(Map<String, ExtractedLine> fields) { return NetDebtDiagnostics.attach(new ValuationResultDTO(), fields, null); }

    @Test void subtractsDisclosedInterestWithoutDoubleDeductingInterbankDeposits() {
        var fields = inputs(); fields.put("accrued_deposit_interest", line("accrued_deposit_interest", "20"));
        fields.put("restricted_interbank", line("restricted_interbank", "500"));
        var result = evaluate(fields);
        assertThat(result.getNetDebtDiagnostic()).isEqualByComparingTo("-880");
        assertThat(result.getNetDebtDiagnosticInputs()).hasSize(9);
        assertThat(result.getDcfValuation()).isNull();
    }

    @Test void rejectsRestrictedCashUnitPeriodEvidenceAndZeroStatusConflicts() {
        for (ExtractedLine bad : List.of(line("restricted_cash", "100").toBuilder().unit("万元").build(),
                line("restricted_cash", "100").toBuilder().asOfDate("2022-12-31").build(),
                line("restricted_cash", "100").toBuilder().snippet("").build(),
                line("restricted_cash", "100").toBuilder().dataQualityStatus("DISCLOSED_ZERO").build())) {
            var fields = inputs(); fields.put("restricted_cash", bad);
            var result = evaluate(fields);
            assertThat(result.getNetDebtDiagnostic()).isNull();
            assertThat(result.getNetDebtDiagnosticMissingItems()).isNotEmpty();
        }
    }

    @Test void doesNotTreatMissingInterestAsProvenZeroOrAmountsAsConfirmedSources() {
        var result = evaluate(inputs());
        assertThat(result.getNetDebtDiagnosticFormula()).contains("应计存款利息未取得，不推定为零");
        var parameters = ValuationParameters.builder().usableCash(new BigDecimal("100")).grossDebt(BigDecimal.ZERO).build();
        assertThat(NetDebtDiagnostics.attach(new ValuationResultDTO(), Map.of(), parameters).getNetDebtDiagnostic()).isNull();
    }
}

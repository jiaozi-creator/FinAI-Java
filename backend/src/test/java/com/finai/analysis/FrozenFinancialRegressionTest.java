package com.finai.analysis;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;

class FrozenFinancialRegressionTest {
    @Test void frozenReportsRetainGrowthAndCorrectColumns() {
        for (String code : new String[]{"000858", "600519"}) {
            StatementExtract extract = new StatementExtractor().extract(Path.of("../data/raw/" + code + "_2023_annual.pdf").toString());
            var lines = extract.getLines().stream().collect(Collectors.toMap(ExtractedLine::getFieldId, Function.identity()));
            var metrics = new MetricCalculator().calculate(extract.getLines(), "2023A");
            for (String id : new String[]{"simple_free_cash_flow", "total_assets", "total_liabilities", "net_assets", "minority_interest", "shares_outstanding", "basic_eps"}) {
                assertThat(metrics.getMetricDetails().get(id + "_growth").getValue()).as(code + " " + id).isNotNull();
                assertThat(metrics.getMetricDetails().get(id + "_growth").getEvidenceRefs()).isNotEmpty();
            }
            assertThat(metrics.getRatios().getConsolidatedNetMargin()).isNotNull();
            assertThat(metrics.getRatios().getAverageEquityRoe()).isNotNull();
            if (code.equals("000858")) {
                assertThat(lines.get("notes_receivable").getCurrent()).as("notes " + lines.get("notes_receivable")).isEqualByComparingTo("0");
                assertThat(lines.get("current_portion_debt").getCurrent()).isEqualByComparingTo("14512448.92");
                assertThat(lines.get("net_profit_deducted").getPrior()).as("deducted candidates " + extract.getCandidates().stream().filter(l -> "net_profit_deducted".equals(l.getFieldId())).toList()).isEqualByComparingTo("26662058958.76");
                assertThat(metrics.getYoyGrowth().getNetProfitDeductedGrowth()).isCloseTo(new BigDecimal("0.12956276"), within(new BigDecimal("0.00000001")));
                assertThat(metrics.getCoreMetrics().getBasicEps()).isEqualByComparingTo("7.783");
                assertThat(metrics.getCoreMetrics().getSimpleFreeCashFlow()).isEqualByComparingTo("38785243225.89");
                assertThat(lines.get("nri_subsidy").getCurrent()).isEqualByComparingTo("236257100.25");
                assertThat(lines.get("nri_fund_occupation").getCurrent()).isEqualByComparingTo("300065.66");
                assertThat(lines.get("nri_impairment_reversal").getCurrent()).isEqualByComparingTo("1388731.60");
                assertThat(lines).doesNotContainKey("nri_fair_value");
            } else {
                assertThat(metrics.getYoyGrowth().getBasicEpsGrowth()).isEqualByComparingTo("0.1916");
                assertThat(metrics.getCoreMetrics().getSimpleFreeCashFlow()).isEqualByComparingTo("63973491832.30");
                assertThat(lines).as("Maotai NRI lines: %s", lines.entrySet().stream()
                        .filter(entry -> entry.getKey().startsWith("nri_")).toList()).containsKey("nri_other");
                assertThat(lines.get("nri_other").getCurrent()).isEqualByComparingTo("-47733771.71");
                assertThat(extract.getPolicyNotes()).anyMatch(note -> note.getPage() == 5 && note.getSnippet().contains("追溯"));
                assertThat(extract.getPolicyNotes()).anyMatch(note -> note.getPage() == 86 && note.getSnippet().contains("136,021,100.77"));
            }
            BigDecimal sum = lines.entrySet().stream().filter(e -> e.getKey().startsWith("nri_") && !e.getKey().equals("nri_total"))
                    .map(e -> e.getKey().equals("nri_tax") || e.getKey().equals("nri_minority_impact") ? e.getValue().getCurrent().negate() : e.getValue().getCurrent())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(sum).as(code + " NRI " + lines.entrySet().stream().filter(e -> e.getKey().startsWith("nri_")).toList()).isEqualByComparingTo(lines.get("nri_total").getCurrent());
        }
    }
}

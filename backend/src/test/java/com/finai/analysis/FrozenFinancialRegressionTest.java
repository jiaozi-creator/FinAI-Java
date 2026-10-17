package com.finai.analysis;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.*;

class FrozenFinancialRegressionTest {
    @Test void frozenReportsRetainGrowthAndCorrectColumns() {
        for (String code : new String[]{"000858", "600519"}) {
            Path pdf = Path.of("../data/raw/" + code + "_2023_annual.pdf");
            Assumptions.assumeTrue(Files.isRegularFile(pdf), "官方年报不在 data/raw，跳过冻结回归: " + pdf.toAbsolutePath().normalize());
            StatementExtract extract = new StatementExtractor().extract(pdf.toString());
            var lines = extract.getLines().stream().collect(Collectors.toMap(ExtractedLine::getFieldId, Function.identity()));
            var metrics = new MetricCalculator().calculate(extract.getLines(), "2023A");
            for (String id : new String[]{"simple_free_cash_flow", "total_assets", "total_liabilities", "net_assets", "minority_interest", "shares_outstanding", "basic_eps"}) {
                assertThat(metrics.getMetricDetails().get(id + "_growth").getValue()).as(code + " " + id).isNotNull();
                assertThat(metrics.getMetricDetails().get(id + "_growth").getEvidenceRefs()).isNotEmpty();
            }
            assertThat(metrics.getRatios().getConsolidatedNetMargin()).isNotNull();
            assertThat(metrics.getRatios().getAverageEquityRoe()).isNotNull();
            assertThat(metrics.getCashFlowReconciliation().getReportedOperatingCashFlow()).isEqualByComparingTo(metrics.getCoreMetrics().getOperatingCashFlow());
            assertThat(metrics.getCashFlowReconciliation().getAdjustments()).extracting(item -> item.getFieldId())
                    .doesNotContain("reconciliation_operating_cash_flow");
            var diagnostic = new ValuationEngine().evaluate(extract.getLines(), metrics);
            assertThat(diagnostic.getNetDebtDiagnosticStatus()).as(code + " diagnostic %s inputs %s", diagnostic.getNetDebtDiagnosticMissingItems(), diagnostic.getNetDebtDiagnosticInputs().stream().map(item -> item.getFieldId() + "=" + item.getAmount() + "/" + item.getUnit() + "/" + item.getPeriod() + "@" + item.getPage()).toList()).isEqualTo("COMPUTED");
            var review = new com.finai.agent.ReviewAgent().review(com.finai.model.dto.AnalysisReportDTO.builder().metrics(metrics).build());
            assertThat(review.getWarnings()).noneMatch(warning -> warning.startsWith("CASH_FLOW_"));
            if (code.equals("000858")) {
                assertThat(metrics.getCashFlowReconciliation().getStatus()).as("Wuliangye CFO reconciliation: %s", metrics.getCashFlowReconciliation().getAdjustments().stream().map(item -> item.getFieldId() + "=" + item.getAmount()).toList()).isEqualTo("RECONCILED");
                assertThat(lines.get("short_term_debt").getCurrent()).isEqualByComparingTo("0");
                assertThat(lines.get("short_term_debt").getDataQualityStatus()).isEqualTo("DISCLOSED_ZERO");
                assertThat(lines.get("restricted_cash").getCurrent()).isEqualByComparingTo("200977259.48");
                assertThat(diagnostic.getNetDebtDiagnostic()).as("diagnostic inputs %s", diagnostic.getNetDebtDiagnosticInputs().stream().map(item -> item.getFieldId() + "=" + item.getAmount() + "@" + item.getPage()).toList()).isEqualByComparingTo("-112965449166.70");
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
                assertThat(metrics.getCashFlowReconciliation().getStatus()).as("Maotai CFO reconciliation: %s", metrics.getCashFlowReconciliation()).isEqualTo("RECONCILED");
                assertThat(lines.get("reconciliation_disposal_loss").getCurrent()).isEqualByComparingTo("479736.97");
                assertThat(lines.get("reconciliation_finance_cost").getCurrent()).isEqualByComparingTo("12624628.35");
                assertThat(lines.get("reconciliation_deferred_tax_assets").getCurrent()).isEqualByComparingTo("-1050934956.22");
                assertThat(lines.get("reconciliation_deferred_tax_liabilities").getCurrent()).isEqualByComparingTo("-83685028.80");
                assertThat(lines.get("reconciliation_credit_impairment").getCurrent()).isEqualByComparingTo("-37871293.26");
                assertThat(lines.get("reconciliation_rou_amortization").getCurrent()).isEqualByComparingTo("72443839.21");
                assertThat(lines.get("reconciliation_fair_value_loss").getCurrent()).isEqualByComparingTo("-3151962.50");
                assertThat(lines.get("total_assets").getPrior()).isEqualByComparingTo("254500826096.02");
                assertThat(lines.get("total_assets").getPeriodType()).isEqualTo("point_in_time");
                assertThat(metrics.getYoyGrowth().getTotalAssetsGrowth()).isEqualByComparingTo("0.07150796");
                assertThat(metrics.getRatios().getRoa()).isEqualByComparingTo("0.29408727");
                assertThat(metrics.getRatios().getAssetLiabilityRatio()).isEqualByComparingTo("0.17984324");
                assertThat(lines.get("total_equity").getCurrent()).isEqualByComparingTo("223656469294.82");
                assertThat(lines.get("accounts_receivable").getCurrent()).isEqualByComparingTo("60373410.41");
                assertThat(lines.get("restricted_cash").getCurrent()).isEqualByComparingTo("5991813679.87");
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

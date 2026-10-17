package com.finai.analysis;

import com.finai.agent.ReviewAgent;
import com.finai.model.dto.AnalysisReportDTO;
import com.finai.model.dto.FinancialMetricsDTO;
import com.finai.model.dto.FinancialMetricsDTO.NonRecurringItem;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ReviewIntegrityTest {
    private NonRecurringItem item(String id, String value, String period) {
        return NonRecurringItem.builder().fieldId(id).amount(new BigDecimal(value)).period(period)
                .unit("元").scope("consolidated").page(1).snippet("本期 上期 合并现金流补充资料").sourceFile("synthetic.txt").build();
    }

    private FinancialMetricsDTO metrics(List<NonRecurringItem> items, String adjustment, String computed, String reported) {
        return FinancialMetricsDTO.builder().period("2023A").unit("元").metricDetails(Map.of())
                .cashFlowReconciliation(FinancialMetricsDTO.CashFlowReconciliation.builder().status("RECONCILED")
                        .consolidatedNetProfit(new BigDecimal("100")).adjustments(items).adjustmentTotal(new BigDecimal(adjustment))
                        .calculatedOperatingCashFlow(new BigDecimal(computed)).reportedOperatingCashFlow(new BigDecimal(reported))
                        .residual(BigDecimal.ZERO).currentPeriod("2023A").scope("consolidated").unit("元").build()).build();
    }

    private List<String> warnings(FinancialMetricsDTO metrics) {
        return new ReviewAgent().review(AnalysisReportDTO.builder().citations(List.of()).metrics(metrics).build()).getWarnings();
    }

    @Test void independentlyChecksReportedCfoDespiteReconciledLabel() {
        var warnings = warnings(metrics(List.of(item("reconciliation_depreciation", "5", "2023A")), "5", "105", "110"));
        assertThat(warnings).contains("CASH_FLOW_CFO_RECALCULATION_FAILED", "CASH_FLOW_RESIDUAL_FAILED");
    }

    @Test void rejectsDuplicateRowsWrongPeriodAndCfoAsAdjustment() {
        var warnings = warnings(metrics(List.of(item("reconciliation_depreciation", "5", "2022A"),
                item("reconciliation_depreciation", "5", "2023A"), item("reconciliation_operating_cash_flow", "105", "2023A")), "115", "215", "215"));
        assertThat(warnings).contains("CASH_FLOW_DUPLICATE_ITEM:reconciliation_depreciation",
                "CASH_FLOW_ITEM_CONTEXT_CONFLICT:reconciliation_depreciation", "CASH_FLOW_RECONCILIATION_INCLUDES_REPORTED_CFO");
    }

    @Test void acceptsConsistentIndependentTotals() {
        assertThat(warnings(metrics(List.of(item("reconciliation_depreciation", "5", "2023A")), "5", "105", "105"))).isEmpty();
    }
}

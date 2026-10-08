package com.finai.agent;

import com.finai.model.dto.AnalysisReportDTO;
import com.finai.model.dto.ValuationResultDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Final deterministic guard against unsupported conclusions and malformed valuation output. */
@Component
public class ReviewAgent {
    public AgentResult review(AnalysisReportDTO report) {
        List<String> warnings = new ArrayList<>();
        if (report == null) warnings.add("REPORT_MISSING");
        if (report != null && report.getCitations() == null) warnings.add("CITATIONS_MISSING");
        if (report != null && report.getMetrics() != null) {
            var details = report.getMetrics().getMetricDetails();
            if (details == null) warnings.add("METRIC_DETAILS_MISSING");
            else details.forEach((id, detail) -> {
                if (detail.getValue() != null && (detail.getEvidenceRefs() == null || detail.getEvidenceRefs().isEmpty())) warnings.add("METRIC_EVIDENCE_MISSING:" + id);
                if (List.of("UNIT_MISMATCH", "PERIOD_MISMATCH", "SCOPE_MISMATCH").contains(detail.getStatus())) warnings.add("METRIC_CONFLICT:" + id);
                if (detail.getEvidenceRefs() != null && detail.getEvidenceRefs().stream().anyMatch(e -> e.getPage() == null || e.getPage() < 1 || e.getSnippet() == null || e.getSnippet().isBlank())) warnings.add("INVALID_EVIDENCE:" + id);
            });
            var items = report.getMetrics().getNonRecurringItems();
            if (items != null) {
                var total = items.stream().filter(i -> "nri_total".equals(i.getFieldId())).findFirst();
                if (total.isPresent() && total.get().getAmount() != null) {
                    java.math.BigDecimal sum = items.stream().filter(i -> !"nri_total".equals(i.getFieldId()) && i.getAmount() != null)
                            .map(i -> List.of("nri_tax", "nri_minority_impact").contains(i.getFieldId()) ? i.getAmount().negate() : i.getAmount())
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    if (sum.subtract(total.get().getAmount()).abs().compareTo(new java.math.BigDecimal("0.01")) > 0) warnings.add("NON_RECURRING_RECONCILIATION_FAILED");
                }
            }
        } else if (report != null) warnings.add("METRICS_MISSING");
        if (report != null && report.getArticulationChecks() != null && report.getArticulationChecks().stream().anyMatch(c -> "FAIL".equals(c.getStatus()))) warnings.add("ARTICULATION_FAILED");
        ValuationResultDTO valuation = report == null ? null : report.getValuation();
        if (valuation != null && valuation.getValuationRange() == null
                && "COMPUTED".equals(valuation.getBusinessStatus())) {
            warnings.add("VALUATION_STATUS_INCONSISTENT");
        }
        if (valuation != null && valuation.getValuationRange() != null && !"COMPUTED".equals(valuation.getBusinessStatus())) warnings.add("VALUATION_STATUS_INCONSISTENT");
        if (valuation != null && valuation.getValuationRange() != null && valuation.getAssumptions() != null
                && valuation.getAssumptions().stream().anyMatch(a -> "default_unconfirmed".equals(a.getSource()))) warnings.add("VALUATION_UNCONFIRMED_INPUT");
        return AgentResult.builder()
                .status(warnings.isEmpty() ? "ok" : "degraded")
                .warnings(warnings)
                .nextAction(warnings.isEmpty() ? "complete" : "review")
                .build();
    }
}

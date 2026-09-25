package com.finai.service;

import com.finai.analysis.ExtractedLine;
import com.finai.analysis.Periods;
import com.finai.analysis.StatementExtract;
import com.finai.model.AnalysisTask;
import com.finai.model.Evidence;
import com.finai.repository.EvidenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EvidenceStore {

    private final EvidenceRepository evidenceRepository;

    @Transactional
    public void replace(AnalysisTask task, StatementExtract extract) {
        evidenceRepository.deleteByTaskId(task.getTaskId());
        evidenceRepository.flush();
        String priorPeriod = Periods.prior(task.getReportPeriod());
        List<Evidence> rows = new ArrayList<>();
        for (ExtractedLine line : extract.getLines()) {
            rows.add(evidence(task, line, task.getReportPeriod(), line.getCurrent()));
            if (line.getPrior() != null && priorPeriod != null) {
                Evidence prior = evidence(task, line, priorPeriod, line.getPrior());
                prior.setCellRef("prior-column");
                rows.add(prior);
            }
        }
        evidenceRepository.saveAll(rows);
    }

    private Evidence evidence(AnalysisTask task, ExtractedLine line, String period, java.math.BigDecimal value) {
        return Evidence.builder()
                .taskId(task.getTaskId())
                .fieldId(line.getFieldId())
                .fieldName(line.getFieldName())
                .value(value)
                .unit(line.getUnit())
                .period(period)
                .scope(line.getScope())
                .sourceFile(line.getSourceFile())
                .page(line.getPage())
                .tableId("statement-line")
                .cellRef("current-column")
                .confidence(line.getConfidence())
                .extractor("pdfbox-line")
                .dictVersion("field-dict-2026.1")
                .snippet(line.getSnippet())
                .build();
    }
}

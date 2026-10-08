package com.finai.service;

import com.finai.model.AnalysisTask;
import com.finai.model.dto.TaskRequestDTO;
import com.finai.repository.AnalysisSnapshotRepository;
import com.finai.repository.AnalysisTaskRepository;
import com.finai.repository.EvidenceRepository;
import com.finai.repository.ValuationConfirmationRepository;
import com.finai.service.impl.AnalysisServiceImpl;
import com.finai.service.impl.IndustryComparisonServiceImpl;
import com.finai.service.impl.RiskWarningServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class P0SafetyTest {
    @Test
    void refusesFabricatedPeerRankings() {
        AnalysisTaskRepository tasks = mock(AnalysisTaskRepository.class);
        when(tasks.existsById("known")).thenReturn(true);
        IndustryComparisonServiceImpl service = new IndustryComparisonServiceImpl(tasks);
        assertThatThrownBy(() -> service.getIndustryRanking("known", "baijiu"))
                .hasMessageContaining("尚未核验");
        assertThatThrownBy(() -> service.getSuggestedPeers("known"))
                .hasMessageContaining("尚未核验");
    }

    @Test
    void unimplementedRiskCategoriesAreNotScoredLow() {
        AnalysisTaskRepository tasks = mock(AnalysisTaskRepository.class);
        when(tasks.findById("known")).thenReturn(Optional.of(AnalysisTask.builder().taskId("known").build()));
        var assessment = new RiskWarningServiceImpl(tasks).getRiskAssessment("known");
        assertThat(assessment.getOverallRiskScore()).isNull();
        assertThat(assessment.getOverallRiskLevel()).isNull();
        assertThat(assessment.getRiskScores().values()).allMatch(score -> score.getScore() == null);
        assertThat(assessment.getSummary()).contains("尚未实现");
    }

    @Test
    void rejectsMissingAndDisguisedPdfBeforeWritingTask() {
        AnalysisServiceImpl service = new AnalysisServiceImpl(mock(AnalysisTaskRepository.class),
                mock(EvidenceRepository.class), mock(AnalysisSnapshotRepository.class),
                mock(AuditLogService.class), null, new ObjectMapper(), null,
                mock(ValuationConfirmationRepository.class));
        TaskRequestDTO request = TaskRequestDTO.builder().companyCode("600519").build();
        assertThatThrownBy(() -> service.createTask(request, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createTask(request,
                new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a PDF".getBytes())))
                .hasMessageContaining("不是 PDF");
    }
}

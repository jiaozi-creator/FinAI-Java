package com.finai.service.impl;

import com.finai.exception.ResourceNotFoundException;
import com.finai.model.AnalysisTask;
import com.finai.model.dto.ComparisonReportDTO;
import com.finai.model.dto.IndustryRankingDTO;
import com.finai.repository.AnalysisTaskRepository;
import com.finai.service.IndustryComparisonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 没有可比公司财报和市价，不输出对比数字。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndustryComparisonServiceImpl implements IndustryComparisonService {

    static final String NOT_COMPUTED = "未计算。没有可比公司的财报和市价，不输出行业排名、分位数或相对优劣。";

    private final AnalysisTaskRepository taskRepository;

    @Override
    public ComparisonReportDTO compareWithPeers(String taskId, List<String> peerCodes) {
        AnalysisTask task = require(taskId);
        log.info("Peer comparison skipped for task {}", taskId);
        return ComparisonReportDTO.builder()
                .reportId(null)
                .targetCompany(ComparisonReportDTO.CompanyInfo.builder()
                        .companyCode(task.getCompanyCode())
                        .companyName(task.getCompanyName())
                        .industry(null)
                        .marketCap(null)
                        .build())
                .peerCompanies(List.of())
                .reportPeriod(task.getReportPeriod())
                .metricComparisons(List.of())
                .rankings(null)
                .strengths(List.of())
                .weaknesses(List.of())
                .summary(NOT_COMPUTED + " 请求的代码：" + (peerCodes == null ? List.of() : peerCodes) + "。这些代码没有对应底稿。")
                .generatedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public IndustryRankingDTO getIndustryRanking(String taskId, String industry) {
        AnalysisTask task = require(taskId);
        log.info("Industry ranking skipped for task {}", taskId);
        return IndustryRankingDTO.builder()
                .companyCode(task.getCompanyCode())
                .companyName(task.getCompanyName())
                .industryCode(industry)
                .industryName(NOT_COMPUTED)
                .reportPeriod(task.getReportPeriod())
                .totalCompanies(null)
                .rankings(null)
                .overallRanking(null)
                .topCompanies(List.of())
                .percentiles(null)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public List<String> getSuggestedPeers(String taskId) {
        require(taskId);
        return List.of();
    }

    @Override
    public ComparisonReportDTO compareWithIndustryAverage(String taskId, String industry) {
        require(taskId);
        return compareWithPeers(taskId, List.of());
    }

    private AnalysisTask require(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
    }
}

package com.finai.service.impl;

import com.finai.model.AnalysisTask;
import com.finai.model.dto.ComparisonReportDTO;
import com.finai.model.dto.IndustryRankingDTO;
import com.finai.repository.AnalysisTaskRepository;
import com.finai.service.IndustryComparisonService;
import com.finai.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 行业对比分析服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndustryComparisonServiceImpl implements IndustryComparisonService {

    private final AnalysisTaskRepository taskRepository;

    @Override
    public ComparisonReportDTO compareWithPeers(String taskId, List<String> peerCodes) {
        log.info("Comparing task {} with peers: {}", taskId, peerCodes);

        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        // 目标公司信息
        ComparisonReportDTO.CompanyInfo targetCompany = ComparisonReportDTO.CompanyInfo.builder()
                .companyCode(task.getCompanyCode())
                .companyName(task.getCompanyName())
                .industry("未分类") // TODO: 从实际数据获取
                .marketCap(0.0)
                .build();

        // 对比公司信息 (示例数据)
        List<ComparisonReportDTO.CompanyInfo> peerCompanies = peerCodes.stream()
                .map(code -> ComparisonReportDTO.CompanyInfo.builder()
                        .companyCode(code)
                        .companyName("公司" + code)
                        .industry(targetCompany.getIndustry())
                        .marketCap(0.0)
                        .build())
                .collect(Collectors.toList());

        // 指标对比
        List<ComparisonReportDTO.MetricComparison> metricComparisons = buildMetricComparisons(
                task, peerCodes);

        // 计算排名
        Map<String, Integer> rankings = calculateRankings(metricComparisons);

        // 识别优劣势
        List<String> strengths = identifyStrengths(metricComparisons);
        List<String> weaknesses = identifyWeaknesses(metricComparisons);

        return ComparisonReportDTO.builder()
                .reportId(UUID.randomUUID().toString())
                .targetCompany(targetCompany)
                .peerCompanies(peerCompanies)
                .reportPeriod(task.getReportPeriod())
                .metricComparisons(metricComparisons)
                .rankings(rankings)
                .strengths(strengths)
                .weaknesses(weaknesses)
                .summary(generateComparisonSummary(strengths, weaknesses))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public IndustryRankingDTO getIndustryRanking(String taskId, String industry) {
        log.info("Getting industry ranking for task {}, industry: {}", taskId, industry);

        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        // TODO: 实现真实的行业排名计算
        Map<String, IndustryRankingDTO.RankingInfo> rankings = buildRankings();

        // 综合排名
        IndustryRankingDTO.RankingInfo overallRanking = IndustryRankingDTO.RankingInfo.builder()
                .metricName("综合得分")
                .rank(25)
                .percentile(75.0)
                .value(85.5)
                .industryAverage(72.3)
                .industryMedian(70.0)
                .maxValue(98.5)
                .minValue(45.2)
                .grade("A")
                .build();

        return IndustryRankingDTO.builder()
                .companyCode(task.getCompanyCode())
                .companyName(task.getCompanyName())
                .industryCode(industry)
                .industryName(getIndustryName(industry))
                .reportPeriod(task.getReportPeriod())
                .totalCompanies(150)
                .rankings(rankings)
                .overallRanking(overallRanking)
                .percentiles(buildPercentiles())
                .generatedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public List<String> getSuggestedPeers(String taskId) {
        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        // TODO: 基于行业、规模、业务模式等推荐对比公司
        return List.of("600036", "000858", "000568"); // 示例：白酒行业对比公司
    }

    @Override
    public ComparisonReportDTO compareWithIndustryAverage(String taskId, String industry) {
        log.info("Comparing task {} with industry average", taskId);

        // 使用行业平均值作为对比对象
        return compareWithPeers(taskId, List.of("INDUSTRY_AVG"));
    }

    /**
     * 构建指标对比
     */
    private List<ComparisonReportDTO.MetricComparison> buildMetricComparisons(
            AnalysisTask task, List<String> peerCodes) {

        List<ComparisonReportDTO.MetricComparison> comparisons = new ArrayList<>();

        // 示例：ROE对比
        Map<String, Double> roeValues = new HashMap<>();
        for (String code : peerCodes) {
            roeValues.put(code, 15.0 + Math.random() * 10);
        }

        comparisons.add(ComparisonReportDTO.MetricComparison.builder()
                .metricName("roe")
                .displayName("净资产收益率")
                .unit("%")
                .targetValue(18.5)
                .peerValues(roeValues)
                .industryAverage(16.2)
                .rank(2)
                .relativeDifference(14.2)
                .assessment("优于行业平均水平")
                .build());

        // 示例：毛利率对比
        Map<String, Double> grossMarginValues = new HashMap<>();
        for (String code : peerCodes) {
            grossMarginValues.put(code, 30.0 + Math.random() * 20);
        }

        comparisons.add(ComparisonReportDTO.MetricComparison.builder()
                .metricName("gross_profit_margin")
                .displayName("毛利率")
                .unit("%")
                .targetValue(42.5)
                .peerValues(grossMarginValues)
                .industryAverage(38.0)
                .rank(1)
                .relativeDifference(11.8)
                .assessment("显著优于行业平均水平")
                .build());

        return comparisons;
    }

    /**
     * 计算排名
     */
    private Map<String, Integer> calculateRankings(
            List<ComparisonReportDTO.MetricComparison> comparisons) {

        Map<String, Integer> rankings = new HashMap<>();
        for (ComparisonReportDTO.MetricComparison comparison : comparisons) {
            rankings.put(comparison.getMetricName(), comparison.getRank());
        }
        return rankings;
    }

    /**
     * 识别优势领域
     */
    private List<String> identifyStrengths(
            List<ComparisonReportDTO.MetricComparison> comparisons) {

        return comparisons.stream()
                .filter(c -> c.getRank() != null && c.getRank() <= 2)
                .map(ComparisonReportDTO.MetricComparison::getDisplayName)
                .collect(Collectors.toList());
    }

    /**
     * 识别劣势领域
     */
    private List<String> identifyWeaknesses(
            List<ComparisonReportDTO.MetricComparison> comparisons) {

        return comparisons.stream()
                .filter(c -> c.getRelativeDifference() != null && c.getRelativeDifference() < -10)
                .map(ComparisonReportDTO.MetricComparison::getDisplayName)
                .collect(Collectors.toList());
    }

    /**
     * 生成对比总结
     */
    private String generateComparisonSummary(List<String> strengths, List<String> weaknesses) {
        StringBuilder summary = new StringBuilder();

        if (!strengths.isEmpty()) {
            summary.append("公司在").append(String.join("、", strengths))
                   .append("等方面表现突出，优于同行业可比公司。");
        }

        if (!weaknesses.isEmpty()) {
            if (summary.length() > 0) summary.append(" ");
            summary.append("但在").append(String.join("、", weaknesses))
                   .append("等方面存在改进空间。");
        }

        if (summary.length() == 0) {
            summary.append("公司整体表现与行业平均水平相当。");
        }

        return summary.toString();
    }

    /**
     * 构建排名信息
     */
    private Map<String, IndustryRankingDTO.RankingInfo> buildRankings() {
        Map<String, IndustryRankingDTO.RankingInfo> rankings = new HashMap<>();

        rankings.put("roe", IndustryRankingDTO.RankingInfo.builder()
                .metricName("净资产收益率")
                .rank(18)
                .percentile(88.0)
                .value(18.5)
                .industryAverage(16.2)
                .industryMedian(15.8)
                .maxValue(25.3)
                .minValue(8.2)
                .grade("A")
                .build());

        rankings.put("gross_profit_margin", IndustryRankingDTO.RankingInfo.builder()
                .metricName("毛利率")
                .rank(12)
                .percentile(92.0)
                .value(42.5)
                .industryAverage(38.0)
                .industryMedian(36.5)
                .maxValue(55.8)
                .minValue(18.3)
                .grade("A+")
                .build());

        return rankings;
    }

    /**
     * 构建分位数信息
     */
    private Map<String, Double> buildPercentiles() {
        Map<String, Double> percentiles = new HashMap<>();
        percentiles.put("P25", 65.0);
        percentiles.put("P50", 70.0);
        percentiles.put("P75", 78.0);
        percentiles.put("P90", 85.0);
        return percentiles;
    }

    /**
     * 获取行业名称
     */
    private String getIndustryName(String industryCode) {
        // TODO: 从配置或数据库获取
        return "白酒制造业";
    }
}

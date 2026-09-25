package com.finai.service.impl;

import com.finai.model.AnalysisTask;
import com.finai.model.dto.RiskAlertDTO;
import com.finai.model.dto.RiskAssessmentDTO;
import com.finai.repository.AnalysisTaskRepository;
import com.finai.service.RiskWarningService;
import com.finai.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 风险预警服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskWarningServiceImpl implements RiskWarningService {

    private final AnalysisTaskRepository taskRepository;

    @Override
    public List<RiskAlertDTO> detectRisks(String taskId) {
        log.info("Detecting risks for task: {}", taskId);

        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        List<RiskAlertDTO> allRisks = new ArrayList<>();

        // 检测各类风险
        allRisks.addAll(detectFraudRisks(taskId));
        allRisks.addAll(detectLiquidityRisks(taskId));
        allRisks.addAll(detectOperationalRisks(taskId));
        allRisks.addAll(detectMarketRisks(taskId));

        // 按风险等级排序
        allRisks.sort(Comparator.comparing(RiskAlertDTO::getRiskLevel).reversed());

        log.info("Detected {} risk alerts for task {}", allRisks.size(), taskId);
        return allRisks;
    }

    @Override
    public RiskAssessmentDTO getRiskAssessment(String taskId) {
        log.info("Generating risk assessment for task: {}", taskId);

        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        // 检测所有风险
        List<RiskAlertDTO> alerts = detectRisks(taskId);

        // 计算各类风险评分
        Map<RiskAlertDTO.RiskType, RiskAssessmentDTO.RiskScore> riskScores = calculateRiskScores(alerts);

        // 计算综合风险评分
        double overallScore = calculateOverallRiskScore(riskScores);
        RiskAlertDTO.RiskLevel overallLevel = getRiskLevel(overallScore);

        // 识别高风险领域
        List<String> highRiskAreas = identifyHighRiskAreas(riskScores);

        // 生成建议
        List<String> recommendations = generateRecommendations(alerts, riskScores);

        return RiskAssessmentDTO.builder()
                .taskId(taskId)
                .companyCode(task.getCompanyCode())
                .companyName(task.getCompanyName())
                .reportPeriod(task.getReportPeriod())
                .overallRiskScore(overallScore)
                .overallRiskLevel(overallLevel)
                .riskScores(riskScores)
                .alerts(alerts)
                .highRiskAreas(highRiskAreas)
                .riskTrend("未计算")
                .summary(generateSummary(overallLevel, highRiskAreas))
                .recommendations(recommendations)
                .assessedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public List<RiskAlertDTO> detectFraudRisks(String taskId) {
        return List.of();
    }

    @Override
    public List<RiskAlertDTO> detectLiquidityRisks(String taskId) {
        return List.of();
    }

    @Override
    public List<RiskAlertDTO> detectOperationalRisks(String taskId) {
        return List.of();
    }

    @Override
    public List<RiskAlertDTO> detectMarketRisks(String taskId) {
        List<RiskAlertDTO> marketRisks = new ArrayList<>();

        // TODO: 实现市场风险检测
        // 1. 行业景气度下降
        // 2. 市场份额减少
        // 3. 竞争加剧
        // 4. 政策变化影响

        return marketRisks;
    }

    /**
     * 计算各类风险评分
     */
    private Map<RiskAlertDTO.RiskType, RiskAssessmentDTO.RiskScore> calculateRiskScores(
            List<RiskAlertDTO> alerts) {

        Map<RiskAlertDTO.RiskType, RiskAssessmentDTO.RiskScore> scores = new HashMap<>();

        // 为每种风险类型计算评分
        for (RiskAlertDTO.RiskType riskType : RiskAlertDTO.RiskType.values()) {
            List<RiskAlertDTO> typeAlerts = alerts.stream()
                    .filter(alert -> alert.getRiskType() == riskType)
                    .collect(Collectors.toList());

            if (typeAlerts.isEmpty()) {
                scores.put(riskType, RiskAssessmentDTO.RiskScore.builder()
                        .score(0.0)
                        .level(RiskAlertDTO.RiskLevel.LOW)
                        .weight(getTypeWeight(riskType))
                        .keyIssues(new ArrayList<>())
                        .build());
                continue;
            }

            // 计算该类型的综合评分
            double typeScore = typeAlerts.stream()
                    .mapToDouble(alert -> getLevelScore(alert.getRiskLevel()) * alert.getConfidence())
                    .average()
                    .orElse(0.0);

            List<String> keyIssues = typeAlerts.stream()
                    .map(RiskAlertDTO::getTitle)
                    .collect(Collectors.toList());

            scores.put(riskType, RiskAssessmentDTO.RiskScore.builder()
                    .score(typeScore)
                    .level(getRiskLevel(typeScore))
                    .weight(getTypeWeight(riskType))
                    .keyIssues(keyIssues)
                    .build());
        }

        return scores;
    }

    /**
     * 计算综合风险评分
     */
    private double calculateOverallRiskScore(
            Map<RiskAlertDTO.RiskType, RiskAssessmentDTO.RiskScore> riskScores) {

        double weightedSum = 0.0;
        double totalWeight = 0.0;

        for (Map.Entry<RiskAlertDTO.RiskType, RiskAssessmentDTO.RiskScore> entry : riskScores.entrySet()) {
            RiskAssessmentDTO.RiskScore score = entry.getValue();
            weightedSum += score.getScore() * score.getWeight();
            totalWeight += score.getWeight();
        }

        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }

    /**
     * 识别高风险领域
     */
    private List<String> identifyHighRiskAreas(
            Map<RiskAlertDTO.RiskType, RiskAssessmentDTO.RiskScore> riskScores) {

        return riskScores.entrySet().stream()
                .filter(entry -> entry.getValue().getLevel() == RiskAlertDTO.RiskLevel.HIGH ||
                               entry.getValue().getLevel() == RiskAlertDTO.RiskLevel.CRITICAL)
                .map(entry -> entry.getKey().getDescription())
                .collect(Collectors.toList());
    }

    /**
     * 生成建议措施
     */
    private List<String> generateRecommendations(
            List<RiskAlertDTO> alerts,
            Map<RiskAlertDTO.RiskType, RiskAssessmentDTO.RiskScore> riskScores) {

        Set<String> recommendations = new LinkedHashSet<>();

        // 收集高优先级预警的建议
        alerts.stream()
                .filter(alert -> alert.getRiskLevel() == RiskAlertDTO.RiskLevel.HIGH ||
                               alert.getRiskLevel() == RiskAlertDTO.RiskLevel.CRITICAL)
                .map(RiskAlertDTO::getRecommendation)
                .filter(Objects::nonNull)
                .forEach(recommendations::add);

        // 如果建议不足3条，添加通用建议
        if (recommendations.size() < 3) {
            recommendations.add("定期监控财务指标变化，及时识别风险信号");
            recommendations.add("加强内部控制，完善风险管理体系");
            recommendations.add("保持充足的流动性储备，确保资金链安全");
        }

        return new ArrayList<>(recommendations);
    }

    /**
     * 生成评估总结
     */
    private String generateSummary(RiskAlertDTO.RiskLevel level, List<String> highRiskAreas) {
        if (level == RiskAlertDTO.RiskLevel.CRITICAL) {
            return "公司面临严重风险，特别是在" + String.join("、", highRiskAreas) +
                   "等方面，建议高度关注并采取紧急措施。";
        } else if (level == RiskAlertDTO.RiskLevel.HIGH) {
            return "公司存在较高风险，主要体现在" + String.join("、", highRiskAreas) +
                   "等领域，需要密切关注并制定应对策略。";
        } else if (level == RiskAlertDTO.RiskLevel.MEDIUM) {
            return "公司整体风险可控，但在某些领域存在中等程度风险，建议持续监控。";
        } else {
            return "公司整体风险较低，财务状况相对健康，建议保持当前管理水平。";
        }
    }

    /**
     * 获取风险类型的权重
     */
    private double getTypeWeight(RiskAlertDTO.RiskType riskType) {
        return switch (riskType) {
            case FRAUD -> 0.30;      // 造假风险权重最高
            case LIQUIDITY -> 0.25;  // 流动性风险
            case OPERATIONAL -> 0.20; // 经营风险
            case MARKET -> 0.15;     // 市场风险
            case CREDIT -> 0.05;     // 信用风险
            case COMPLIANCE -> 0.05; // 合规风险
        };
    }

    /**
     * 将风险等级转换为分数
     */
    private double getLevelScore(RiskAlertDTO.RiskLevel level) {
        return switch (level) {
            case LOW -> 25.0;
            case MEDIUM -> 50.0;
            case HIGH -> 75.0;
            case CRITICAL -> 100.0;
        };
    }

    /**
     * 根据分数确定风险等级
     */
    private RiskAlertDTO.RiskLevel getRiskLevel(double score) {
        if (score >= 75) return RiskAlertDTO.RiskLevel.CRITICAL;
        if (score >= 50) return RiskAlertDTO.RiskLevel.HIGH;
        if (score >= 25) return RiskAlertDTO.RiskLevel.MEDIUM;
        return RiskAlertDTO.RiskLevel.LOW;
    }
}

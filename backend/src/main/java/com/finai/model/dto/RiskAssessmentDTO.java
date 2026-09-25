package com.finai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 风险评估报告 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessmentDTO {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 公司代码
     */
    private String companyCode;

    /**
     * 公司名称
     */
    private String companyName;

    /**
     * 报告期
     */
    private String reportPeriod;

    /**
     * 综合风险评分 (0-100，越高风险越大)
     */
    private Double overallRiskScore;

    /**
     * 综合风险等级
     */
    private RiskAlertDTO.RiskLevel overallRiskLevel;

    /**
     * 各类风险评分
     */
    private Map<RiskAlertDTO.RiskType, RiskScore> riskScores;

    /**
     * 检测到的风险预警列表
     */
    private List<RiskAlertDTO> alerts;

    /**
     * 高风险领域
     */
    private List<String> highRiskAreas;

    /**
     * 风险趋势 (相比上一期)
     */
    private String riskTrend;

    /**
     * 总体评估
     */
    private String summary;

    /**
     * 建议措施
     */
    private List<String> recommendations;

    /**
     * 评估时间
     */
    private LocalDateTime assessedAt;

    /**
     * 风险评分详情
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskScore {
        /**
         * 分数 (0-100)
         */
        private Double score;

        /**
         * 等级
         */
        private RiskAlertDTO.RiskLevel level;

        /**
         * 权重
         */
        private Double weight;

        /**
         * 主要问题
         */
        private List<String> keyIssues;
    }
}

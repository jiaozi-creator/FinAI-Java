package com.finai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 行业排名 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndustryRankingDTO {

    /**
     * 公司代码
     */
    private String companyCode;

    /**
     * 公司名称
     */
    private String companyName;

    /**
     * 行业代码
     */
    private String industryCode;

    /**
     * 行业名称
     */
    private String industryName;

    /**
     * 报告期
     */
    private String reportPeriod;

    /**
     * 行业内总公司数
     */
    private Integer totalCompanies;

    /**
     * 各指标排名
     */
    private Map<String, RankingInfo> rankings;

    /**
     * 综合排名
     */
    private RankingInfo overallRanking;

    /**
     * 排名前10的公司
     */
    private List<CompanyRanking> topCompanies;

    /**
     * 分位数信息
     */
    private Map<String, Double> percentiles;

    /**
     * 生成时间
     */
    private LocalDateTime generatedAt;

    /**
     * 排名信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RankingInfo {
        /**
         * 指标名称
         */
        private String metricName;

        /**
         * 排名
         */
        private Integer rank;

        /**
         * 百分位
         */
        private Double percentile;

        /**
         * 指标值
         */
        private Double value;

        /**
         * 行业平均值
         */
        private Double industryAverage;

        /**
         * 行业中位数
         */
        private Double industryMedian;

        /**
         * 最大值
         */
        private Double maxValue;

        /**
         * 最小值
         */
        private Double minValue;

        /**
         * 评级 (A+, A, B+, B, C+, C, D)
         */
        private String grade;
    }

    /**
     * 公司排名
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyRanking {
        private Integer rank;
        private String companyCode;
        private String companyName;
        private Double score;
        private Map<String, Double> keyMetrics;
    }
}

package com.finai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 对比报告 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonReportDTO {

    /**
     * 报告ID
     */
    private String reportId;

    /**
     * 目标公司
     */
    private CompanyInfo targetCompany;

    /**
     * 对比公司列表
     */
    private List<CompanyInfo> peerCompanies;

    /**
     * 报告期
     */
    private String reportPeriod;

    /**
     * 对比指标
     */
    private List<MetricComparison> metricComparisons;

    /**
     * 排名信息
     */
    private Map<String, Integer> rankings;

    /**
     * 优势领域
     */
    private List<String> strengths;

    /**
     * 劣势领域
     */
    private List<String> weaknesses;

    /**
     * 分析总结
     */
    private String summary;

    /**
     * 生成时间
     */
    private LocalDateTime generatedAt;

    /**
     * 公司信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyInfo {
        private String companyCode;
        private String companyName;
        private String industry;
        private Double marketCap;
    }

    /**
     * 指标对比
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricComparison {
        /**
         * 指标名称
         */
        private String metricName;

        /**
         * 指标显示名称
         */
        private String displayName;

        /**
         * 单位
         */
        private String unit;

        /**
         * 目标公司值
         */
        private Double targetValue;

        /**
         * 对比公司值
         */
        private Map<String, Double> peerValues;

        /**
         * 行业平均值
         */
        private Double industryAverage;

        /**
         * 目标公司排名
         */
        private Integer rank;

        /**
         * 相对差异 (相比行业平均)
         */
        private Double relativeDifference;

        /**
         * 评价
         */
        private String assessment;
    }
}

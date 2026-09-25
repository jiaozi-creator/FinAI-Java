package com.finai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分析报告DTO
 *
 * 完整的结构化报告
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisReportDTO {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 报告标题
     */
    private String title;

    /**
     * 公司概况
     */
    private CompanyOverview companyOverview;

    /**
     * 财务指标看板
     */
    private FinancialMetricsDTO metrics;

    /**
     * 异常信号列表
     */
    private List<AnomalySignalDTO> anomalies;

    /**
     * 财务质量分析
     */
    private FinancialQualityAnalysis qualityAnalysis;

    /**
     * 估值结果(可选)
     */
    private ValuationResultDTO valuation;

    /**
     * 主要风险
     */
    private List<RiskFactor> risks;

    /**
     * 结论与建议
     */
    private String conclusion;

    /**
     * 来源引用
     */
    private List<Citation> citations;

    /**
     * 运行摘要
     */
    private ExecutionSummary executionSummary;

    /**
     * 勾稽
     */
    private List<ArticulationItem> articulationChecks;

    /**
     * 公司概况
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyOverview {
        private String companyCode;
        private String companyName;
        private String industry;
        private String sector;
        private String reportPeriod;
        private String reportType;
    }

    /**
     * 财务质量分析
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialQualityAnalysis {
        private String growthAnalysis;      // 成长能力分析
        private String profitabilityAnalysis; // 盈利能力分析
        private String cashFlowAnalysis;    // 现金流质量分析
        private String operationAnalysis;   // 营运能力分析
        private String solvencyAnalysis;    // 偿债能力分析
        private String overallAssessment;   // 综合评估
    }

    /**
     * 风险因素
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskFactor {
        private String category;
        private String description;
        private String severity;
        private List<String> evidence;
    }

    /**
     * 引用
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Citation {
        private String sourceFile;
        private Integer page;
        private String tableId;
        private String description;
    }

    /**
     * 勾稽项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArticulationItem {
        private String name;
        private String status;
        private String detail;
        private String statementType;
    }

    /**
     * 执行摘要
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionSummary {
        private String llmModel;
        private Integer llmCallCount;
        private Long executionTimeMs;
        private String dataVersion;
        private String configVersion;
        private List<String> toolsUsed;
        private String generatedAt;
    }
}

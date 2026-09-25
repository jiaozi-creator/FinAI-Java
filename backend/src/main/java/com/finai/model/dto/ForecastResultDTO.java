package com.finai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 预测结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastResultDTO {

    /**
     * 预测ID
     */
    private String forecastId;

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
     * 基准期
     */
    private String basePeriod;

    /**
     * 预测期数
     */
    private Integer periods;

    /**
     * 预测方法
     */
    private String method;

    /**
     * 指标预测
     */
    private List<MetricForecast> metricForecasts;

    /**
     * 场景分析
     */
    private Map<String, ScenarioForecast> scenarios;

    /**
     * 趋势分析
     */
    private List<TrendAnalysis> trendAnalyses;

    /**
     * 模型评估指标
     */
    private ModelPerformance modelPerformance;

    /**
     * 关键假设
     */
    private List<String> keyAssumptions;

    /**
     * 风险提示
     */
    private List<String> riskWarnings;

    /**
     * 生成时间
     */
    private LocalDateTime generatedAt;

    /**
     * 指标预测
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricForecast {
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
         * 历史值
         */
        private List<TimeSeriesPoint> historicalValues;

        /**
         * 预测值
         */
        private List<TimeSeriesPoint> forecastValues;

        /**
         * 置信区间上限
         */
        private List<TimeSeriesPoint> upperBound;

        /**
         * 置信区间下限
         */
        private List<TimeSeriesPoint> lowerBound;

        /**
         * 置信水平 (如 0.95 表示 95% 置信区间)
         */
        private Double confidenceLevel;
    }

    /**
     * 时间序列点
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSeriesPoint {
        private String period;
        private Double value;
        private LocalDateTime timestamp;
    }

    /**
     * 场景预测
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScenarioForecast {
        /**
         * 场景名称
         */
        private String scenarioName;

        /**
         * 场景描述
         */
        private String description;

        /**
         * 概率
         */
        private Double probability;

        /**
         * 各指标预测值
         */
        private Map<String, List<TimeSeriesPoint>> metricForecasts;

        /**
         * 关键驱动因素
         */
        private List<String> keyDrivers;
    }

    /**
     * 趋势分析
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendAnalysis {
        /**
         * 指标名称
         */
        private String metricName;

        /**
         * 趋势类型
         */
        private TrendType trendType;

        /**
         * 趋势强度 (0-1)
         */
        private Double trendStrength;

        /**
         * 增长率 (年化)
         */
        private Double growthRate;

        /**
         * 波动性
         */
        private Double volatility;

        /**
         * 拐点检测
         */
        private List<TurningPoint> turningPoints;

        /**
         * 趋势描述
         */
        private String description;
    }

    /**
     * 趋势类型
     */
    public enum TrendType {
        UPWARD("上升趋势"),
        DOWNWARD("下降趋势"),
        STABLE("稳定"),
        CYCLICAL("周期性"),
        VOLATILE("波动性");

        private final String description;

        TrendType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 拐点
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TurningPoint {
        private String period;
        private String type; // "peak" 或 "trough"
        private Double value;
        private String description;
    }

    /**
     * 模型性能指标
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelPerformance {
        /**
         * 平均绝对误差 (MAE)
         */
        private Double mae;

        /**
         * 均方根误差 (RMSE)
         */
        private Double rmse;

        /**
         * 平均绝对百分比误差 (MAPE)
         */
        private Double mape;

        /**
         * R平方
         */
        private Double rSquared;

        /**
         * 模型置信度
         */
        private Double confidence;
    }
}

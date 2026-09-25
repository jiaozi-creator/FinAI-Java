package com.finai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 估值结果DTO
 *
 * 包含DCF、相对估值、历史分位等估值结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValuationResultDTO {

    /**
     * DCF估值结果
     */
    private DCFValuation dcfValuation;

    /**
     * 相对估值结果
     */
    private RelativeValuation relativeValuation;

    /**
     * 历史分位结果
     */
    private HistoricalPercentile historicalPercentile;

    /**
     * 敏感性分析
     */
    private SensitivityAnalysis sensitivityAnalysis;

    /**
     * 估值假设
     */
    private List<ValuationAssumption> assumptions;

    /**
     * 估值区间
     */
    private ValuationRange valuationRange;

    /**
     * DCF估值
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DCFValuation {
        private Scenario baseCase;      // 基准情景
        private Scenario optimistic;    // 乐观情景
        private Scenario pessimistic;   // 悲观情景
        private BigDecimal wacc;        // WACC
        private BigDecimal terminalGrowthRate; // 永续增长率

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Scenario {
            private String name;
            private BigDecimal enterpriseValue;  // 企业价值
            private BigDecimal equityValue;      // 股权价值
            private BigDecimal valuePerShare;    // 每股价值
            private List<BigDecimal> freeCashFlows; // 未来现金流
            private BigDecimal terminalValue;    // 终值
        }
    }

    /**
     * 相对估值
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelativeValuation {
        private PEValuation pe;            // PE估值
        private PBValuation pb;            // PB估值
        private EVEBITDAValuation evEbitda; // EV/EBITDA估值

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PEValuation {
            private BigDecimal currentPE;
            private BigDecimal industryAvgPE;
            private BigDecimal comparablePE;
            private BigDecimal impliedValue;
            private List<ComparableCompany> comparables;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PBValuation {
            private BigDecimal currentPB;
            private BigDecimal industryAvgPB;
            private BigDecimal comparablePB;
            private BigDecimal impliedValue;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class EVEBITDAValuation {
            private BigDecimal currentEVEBITDA;
            private BigDecimal industryAvgEVEBITDA;
            private BigDecimal comparableEVEBITDA;
            private BigDecimal impliedValue;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ComparableCompany {
            private String code;
            private String name;
            private BigDecimal pe;
            private BigDecimal pb;
            private BigDecimal roe;
            private BigDecimal revenueGrowth;
        }
    }

    /**
     * 历史分位
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoricalPercentile {
        private MetricPercentile pe;
        private MetricPercentile pb;
        private Integer windowYears;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class MetricPercentile {
            private BigDecimal currentValue;
            private BigDecimal percentile;
            private BigDecimal min;
            private BigDecimal max;
            private BigDecimal median;
            private String interpretation;
        }
    }

    /**
     * 敏感性分析
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SensitivityAnalysis {
        private List<BigDecimal> waccRange;
        private List<BigDecimal> terminalGrowthRange;
        private List<List<BigDecimal>> valuationMatrix; // WACC x 终值增长率矩阵
    }

    /**
     * 估值假设
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValuationAssumption {
        private String parameter;
        private String value;
        private String source;      // extract, formula, user, llm_suggest
        private String rationale;
        private Double confidence;
    }

    /**
     * 估值区间
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValuationRange {
        private BigDecimal low;
        private BigDecimal mid;
        private BigDecimal high;
        private String methodology;
        private String applicabilityNote;
    }
}

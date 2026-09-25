package com.finai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 财务指标DTO
 *
 * 包含核心财务指标及其计算结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialMetricsDTO {

    /**
     * 报告期
     */
    private String period;

    /**
     * 上一报告期。环比没有多期序列时为空。
     */
    private String priorPeriod;

    /**
     * 列示单位，未换算成元
     */
    private String unit;

    /**
     * consolidated / parent / unknown
     */
    private String scope;

    /**
     * 核心指标
     */
    private CoreMetrics coreMetrics;

    /**
     * 同比增长率
     */
    private GrowthRates yoyGrowth;

    /**
     * 环比增长率
     */
    private GrowthRates qoqGrowth;

    /**
     * 财务比率
     */
    private FinancialRatios ratios;

    /**
     * 非经常性损益明细。没有该表时为空。
     */
    private List<NonRecurringItem> nonRecurringItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NonRecurringItem {
        private String fieldId;
        private String name;
        private BigDecimal amount;
        private Integer page;
        private String snippet;
    }

    /**
     * 核心指标
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoreMetrics {
        private BigDecimal revenue;                 // 营业收入
        private BigDecimal netProfit;               // 归母净利润
        private BigDecimal netProfitDeducted;       // 扣非净利润
        private BigDecimal operatingCashFlow;       // 经营现金流
        private BigDecimal freeCashFlow;            // 自由现金流
        private BigDecimal totalAssets;             // 总资产
        private BigDecimal totalLiabilities;        // 总负债
        private BigDecimal netAssets;               // 净资产
        private BigDecimal accountsReceivable;      // 应收账款
        private BigDecimal inventory;               // 存货
        private BigDecimal goodwill;                // 商誉
        private BigDecimal minorityInterest;        // 少数股东权益
        private BigDecimal sharesOutstanding;       // 期末总股本
        private BigDecimal basicEps;                // 基本每股收益
    }

    /**
     * 增长率
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrowthRates {
        private BigDecimal revenueGrowth;           // 收入增长率
        private BigDecimal netProfitGrowth;         // 净利润增长率
        private BigDecimal netProfitDeductedGrowth; // 扣非净利润增长率
        private BigDecimal operatingCashFlowGrowth; // 经营现金流增长率
    }

    /**
     * 财务比率
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinancialRatios {
        private BigDecimal grossMargin;             // 毛利率
        private BigDecimal netMargin;               // 净利率
        private BigDecimal roe;                     // ROE
        private BigDecimal roa;                     // ROA
        private BigDecimal assetLiabilityRatio;     // 资产负债率
        private BigDecimal currentRatio;            // 流动比率
        private BigDecimal quickRatio;              // 速动比率
        private BigDecimal cashToDebtRatio;         // 现金短债比
        private BigDecimal ocfToNetProfit;          // 经营现金流/净利润
        private BigDecimal accountsReceivableTurnover; // 应收账款周转率
        private BigDecimal inventoryTurnover;       // 存货周转率
    }
}

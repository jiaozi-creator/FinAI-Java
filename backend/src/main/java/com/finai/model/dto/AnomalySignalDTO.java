package com.finai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 异常信号DTO
 *
 * 包含检测到的财务异常及其证据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalySignalDTO {

    /**
     * 异常ID
     */
    private String anomalyId;

    /**
     * 异常名称
     */
    private String name;

    /**
     * 异常类型
     */
    private AnomalyType type;

    /**
     * 严重程度
     */
    private Severity severity;

    /**
     * 描述
     */
    private String description;

    /**
     * 触发规则
     */
    private String ruleTriggered;

    /**
     * 规则版本
     */
    private String ruleVersion;

    /**
     * 实际值
     */
    private BigDecimal actualValue;

    /**
     * 阈值
     */
    private BigDecimal threshold;

    /**
     * 偏差
     */
    private BigDecimal deviation;

    /**
     * 影响指标
     */
    private List<String> affectedMetrics;

    /**
     * 证据列表
     */
    private List<EvidenceDTO> evidence;

    /**
     * 二次核验状态
     */
    private VerificationStatus verificationStatus;

    /**
     * 核验详情
     */
    private String verificationDetails;

    /**
     * 建议
     */
    private String recommendation;

    /**
     * 异常类型枚举
     */
    public enum AnomalyType {
        REVENUE_PROFIT_MISMATCH,    // 增收不增利
        PROFIT_CASHFLOW_DIVERGENCE, // 利润与现金流背离
        ACCOUNTS_RECEIVABLE_HIGH,   // 应收账款异常
        INVENTORY_ACCUMULATION,     // 存货积压
        GOODWILL_IMPAIRMENT,        // 商誉减值风险
        DEBT_PRESSURE,              // 偿债压力
        NON_RECURRING_ITEMS,        // 非经常性损益占比高
        MARGIN_DECLINE,             // 利润率下滑
        RELATED_PARTY_TRANSACTIONS, // 关联交易异常
        ACCOUNTING_POLICY_CHANGE,   // 会计政策变更
        OTHER                       // 其他
    }

    /**
     * 严重程度枚举
     */
    public enum Severity {
        HIGH,      // 高
        MEDIUM,    // 中
        LOW        // 低
    }

    /**
     * 核验状态枚举
     */
    public enum VerificationStatus {
        CONFIRMED,      // 已确认
        INCONCLUSIVE,   // 不确定
        FALSE_POSITIVE, // 误报
        PENDING         // 待核验
    }
}

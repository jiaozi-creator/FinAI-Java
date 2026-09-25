package com.finai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 风险预警 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAlertDTO {

    /**
     * 预警ID
     */
    private String alertId;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 风险类型
     */
    private RiskType riskType;

    /**
     * 风险等级
     */
    private RiskLevel riskLevel;

    /**
     * 风险标题
     */
    private String title;

    /**
     * 风险描述
     */
    private String description;

    /**
     * 触发指标
     */
    private String triggerMetric;

    /**
     * 指标值
     */
    private Object metricValue;

    /**
     * 阈值
     */
    private Object threshold;

    /**
     * 影响分析
     */
    private String impact;

    /**
     * 建议措施
     */
    private String recommendation;

    /**
     * 置信度 (0-1)
     */
    private Double confidence;

    /**
     * 检测时间
     */
    private LocalDateTime detectedAt;

    /**
     * 证据引用
     */
    private String[] evidenceIds;

    /**
     * 风险类型枚举
     */
    public enum RiskType {
        FRAUD("财务造假风险"),
        LIQUIDITY("流动性风险"),
        OPERATIONAL("经营风险"),
        MARKET("市场风险"),
        CREDIT("信用风险"),
        COMPLIANCE("合规风险");

        private final String description;

        RiskType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 风险等级枚举
     */
    public enum RiskLevel {
        LOW("低风险"),
        MEDIUM("中等风险"),
        HIGH("高风险"),
        CRITICAL("严重风险");

        private final String description;

        RiskLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}

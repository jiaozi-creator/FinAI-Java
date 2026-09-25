package com.finai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 报告模板 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportTemplateDTO {

    /**
     * 模板ID
     */
    private String templateId;

    /**
     * 模板名称
     */
    private String templateName;

    /**
     * 模板描述
     */
    private String description;

    /**
     * 模板类型
     */
    private TemplateType templateType;

    /**
     * 模板内容（Markdown格式）
     */
    private String templateContent;

    /**
     * 章节定义
     */
    private List<Section> sections;

    /**
     * 是否为默认模板
     */
    private Boolean isDefault;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 创建者
     */
    private String createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 使用次数
     */
    private Integer usageCount;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 模板类型
     */
    public enum TemplateType {
        STANDARD("标准模板"),
        CUSTOM("自定义模板"),
        SYSTEM("系统模板");

        private final String description;

        TemplateType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 章节定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Section {
        /**
         * 章节ID
         */
        private String sectionId;

        /**
         * 章节标题
         */
        private String title;

        /**
         * 章节顺序
         */
        private Integer order;

        /**
         * 是否必需
         */
        private Boolean required;

        /**
         * 数据源
         */
        private DataSource dataSource;

        /**
         * 内容模板
         */
        private String contentTemplate;

        /**
         * 子章节
         */
        private List<Section> subsections;
    }

    /**
     * 数据源定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataSource {
        /**
         * 数据类型
         */
        private DataType dataType;

        /**
         * API端点
         */
        private String apiEndpoint;

        /**
         * 数据字段映射
         */
        private Map<String, String> fieldMapping;

        /**
         * 过滤条件
         */
        private Map<String, Object> filters;
    }

    /**
     * 数据类型
     */
    public enum DataType {
        COMPANY_INFO("公司信息"),
        FINANCIAL_METRICS("财务指标"),
        RISK_ASSESSMENT("风险评估"),
        ANOMALY_SIGNALS("异常信号"),
        VALUATION("估值结果"),
        COMPARISON("行业对比"),
        FORECAST("预测分析"),
        CHAT_SUMMARY("对话摘要"),
        CUSTOM("自定义数据");

        private final String description;

        DataType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 生成的报告
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeneratedReport {
        private String reportId;
        private String taskId;
        private String templateId;
        private String templateName;
        private String content;
        private ReportFormat format;
        private String filePath;
        private LocalDateTime generatedAt;
    }

    /**
     * 报告格式
     */
    public enum ReportFormat {
        MARKDOWN("Markdown"),
        HTML("HTML"),
        WORD("Word"),
        PDF("PDF");

        private final String description;

        ReportFormat(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}

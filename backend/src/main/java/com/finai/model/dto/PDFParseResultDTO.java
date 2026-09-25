package com.finai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * PDF 解析结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PDFParseResultDTO {

    /**
     * 解析ID
     */
    private String parseId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 总页数
     */
    private Integer totalPages;

    /**
     * 报表类型
     */
    private ReportType reportType;

    /**
     * 公司信息
     */
    private CompanyInfo companyInfo;

    /**
     * 报告期
     */
    private String reportPeriod;

    /**
     * 提取的表格
     */
    private List<TableData> tables;

    /**
     * 提取的图表
     */
    private List<ChartData> charts;

    /**
     * 提取的财务指标
     */
    private Map<String, Object> financialMetrics;

    /**
     * 文本内容
     */
    private List<TextBlock> textBlocks;

    /**
     * 附注信息
     */
    private List<NoteInfo> notes;

    /**
     * 解析状态
     */
    private ParseStatus status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 解析时间
     */
    private LocalDateTime parsedAt;

    /**
     * 置信度
     */
    private Double confidence;

    /**
     * 报表类型枚举
     */
    public enum ReportType {
        ANNUAL_REPORT("年度报告"),
        SEMI_ANNUAL_REPORT("半年度报告"),
        QUARTERLY_REPORT("季度报告"),
        PROSPECTUS("招股说明书"),
        OTHER("其他");

        private final String description;

        ReportType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 解析状态
     */
    public enum ParseStatus {
        SUCCESS("成功"),
        PARTIAL_SUCCESS("部分成功"),
        FAILED("失败");

        private final String description;

        ParseStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

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
        private String legalRepresentative;
        private String registeredAddress;
    }

    /**
     * 表格数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableData {
        /**
         * 表格ID
         */
        private String tableId;

        /**
         * 表格标题
         */
        private String title;

        /**
         * 页码
         */
        private Integer pageNumber;

        /**
         * 表格位置
         */
        private Rectangle position;

        /**
         * 表头
         */
        private List<String> headers;

        /**
         * 数据行
         */
        private List<List<String>> rows;

        /**
         * 结构化数据（键值对）
         */
        private Map<String, Map<String, Object>> structuredData;

        /**
         * 表格类型
         */
        private TableType tableType;

        /**
         * 识别置信度
         */
        private Double confidence;
    }

    /**
     * 表格类型
     */
    public enum TableType {
        BALANCE_SHEET("资产负债表"),
        INCOME_STATEMENT("利润表"),
        CASH_FLOW_STATEMENT("现金流量表"),
        FINANCIAL_INDICATORS("主要财务指标"),
        OTHER("其他");

        private final String description;

        TableType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 图表数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartData {
        /**
         * 图表ID
         */
        private String chartId;

        /**
         * 图表标题
         */
        private String title;

        /**
         * 页码
         */
        private Integer pageNumber;

        /**
         * 图表位置
         */
        private Rectangle position;

        /**
         * 图表类型
         */
        private ChartType chartType;

        /**
         * 图片路径（保存的截图）
         */
        private String imagePath;

        /**
         * OCR识别的数据
         */
        private Map<String, Object> extractedData;

        /**
         * 描述
         */
        private String description;
    }

    /**
     * 图表类型
     */
    public enum ChartType {
        BAR_CHART("柱状图"),
        LINE_CHART("折线图"),
        PIE_CHART("饼图"),
        TABLE_IMAGE("表格图片"),
        OTHER("其他");

        private final String description;

        ChartType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 文本块
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextBlock {
        private Integer pageNumber;
        private String text;
        private Rectangle position;
        private String section; // 所属章节
    }

    /**
     * 矩形位置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Rectangle {
        private Double x;
        private Double y;
        private Double width;
        private Double height;
    }

    /**
     * 附注信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NoteInfo {
        /**
         * 附注编号
         */
        private String noteNumber;

        /**
         * 附注标题
         */
        private String title;

        /**
         * 附注内容
         */
        private String content;

        /**
         * 页码
         */
        private Integer pageNumber;

        /**
         * 相关指标
         */
        private List<String> relatedMetrics;
    }

    /**
     * 多年报表对齐数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlignedData {
        /**
         * 年度列表
         */
        private List<String> years;

        /**
         * 对齐的指标数据
         */
        private Map<String, List<Object>> alignedMetrics;

        /**
         * 缺失数据标记
         */
        private Map<String, List<Boolean>> missingDataFlags;

        /**
         * 数据来源
         */
        private Map<String, List<String>> dataSources;
    }
}

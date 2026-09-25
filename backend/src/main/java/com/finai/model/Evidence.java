package com.finai.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 证据实体 - 记录所有财务数据的来源
 *
 * 每条证据记录包含:
 * - 字段标识和数值
 * - 原始文件和位置(页码、表格、单元格)
 * - 解析器信息和置信度
 * - 报告期和合并口径
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "evidence", indexes = {
    @Index(name = "idx_task_id", columnList = "task_id"),
    @Index(name = "idx_field_id", columnList = "field_id"),
    @Index(name = "idx_period", columnList = "period")
})
public class Evidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 关联的任务ID
     */
    @Column(name = "task_id", nullable = false)
    private String taskId;

    /**
     * 字段标识 (如: revenue, net_profit)
     */
    @Column(name = "field_id", nullable = false, length = 100)
    private String fieldId;

    /**
     * 字段中文名称
     */
    @Column(name = "field_name", length = 100)
    private String fieldName;

    /**
     * 数值 (存储为字符串保持精度)
     */
    @Column(name = "metric_value", precision = 20, scale = 4)
    private BigDecimal value;

    /**
     * 单位 (CNY, USD, 万元, 亿元等)
     */
    @Column(name = "unit", length = 20)
    private String unit;

    /**
     * 报告期 (如: 2023A, 2023Q3)
     */
    @Column(name = "period", nullable = false, length = 20)
    private String period;

    /**
     * 合并口径 (consolidated, parent_only等)
     */
    @Column(name = "scope", length = 50)
    private String scope;

    /**
     * 原始文件路径
     */
    @Column(name = "source_file", length = 500)
    private String sourceFile;

    /**
     * 文件哈希
     */
    @Column(name = "file_hash", length = 100)
    private String fileHash;

    /**
     * 页码
     */
    @Column(name = "page")
    private Integer page;

    /**
     * 表格ID
     */
    @Column(name = "table_id", length = 100)
    private String tableId;

    /**
     * 单元格引用 (如: R12C3)
     */
    @Column(name = "cell_ref", length = 20)
    private String cellRef;

    /**
     * 置信度 (0.0-1.0)
     */
    @Column(name = "confidence")
    private Double confidence;

    /**
     * 解析器名称 (pdfbox, ocr等)
     */
    @Column(name = "extractor", length = 50)
    private String extractor;

    /**
     * 字段字典版本
     */
    @Column(name = "dict_version", length = 50)
    private String dictVersion;

    /**
     * 原始文本片段
     */
    @Column(name = "snippet", length = 1000)
    private String snippet;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

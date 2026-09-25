package com.finai.model.dto;

import com.finai.model.Evidence;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 证据DTO
 *
 * 用于API响应中传递证据信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceDTO {

    /**
     * 证据ID
     */
    private Long id;

    /**
     * 字段标识
     */
    private String fieldId;

    /**
     * 字段名称
     */
    private String fieldName;

    /**
     * 数值
     */
    private String value;

    /**
     * 单位
     */
    private String unit;

    /**
     * 报告期
     */
    private String period;

    /**
     * 原始文件
     */
    private String sourceFile;

    /**
     * 页码
     */
    private Integer page;

    /**
     * 表格ID
     */
    private String tableId;

    /**
     * 单元格引用
     */
    private String cellRef;

    /**
     * 置信度
     */
    private Double confidence;

    /**
     * 文本片段
     */
    private String snippet;

    /**
     * 从实体转换
     */
    public static EvidenceDTO fromEntity(Evidence evidence) {
        if (evidence == null) {
            return null;
        }
        return EvidenceDTO.builder()
                .id(evidence.getId())
                .fieldId(evidence.getFieldId())
                .fieldName(evidence.getFieldName())
                .value(evidence.getValue() != null ? evidence.getValue().toString() : null)
                .unit(evidence.getUnit())
                .period(evidence.getPeriod())
                .sourceFile(evidence.getSourceFile())
                .page(evidence.getPage())
                .tableId(evidence.getTableId())
                .cellRef(evidence.getCellRef())
                .confidence(evidence.getConfidence())
                .snippet(evidence.getSnippet())
                .build();
    }
}

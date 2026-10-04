package com.finai.analysis;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * 从财报文本中定位到的一行数字。current / prior 都是原文列示值，未做单位换算。
 */
@Value
@Builder(toBuilder = true)
public class ExtractedLine {
    String fieldId;
    String fieldName;
    BigDecimal current;
    BigDecimal prior;
    String unit;
    String scope;
    Integer page;
    String snippet;
    Double confidence;
    String sourceFile;
    String statementType;
    String sourceSection;
    String periodType;
    String periodStart;
    String periodEnd;
    String asOfDate;
    String currentColumn;
    String priorColumn;
    Integer candidateRank;
    String selectionStatus;
    String selectionReason;
    String dataQualityStatus;
    String missingReason;
}

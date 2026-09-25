package com.finai.analysis;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * 从财报文本中定位到的一行数字。current / prior 都是原文列示值，未做单位换算。
 */
@Value
@Builder
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
}

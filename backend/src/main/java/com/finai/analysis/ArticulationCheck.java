package com.finai.analysis;

import lombok.Builder;
import lombok.Value;

/**
 * 勾稽结果。status: PASS / FAIL / SKIPPED。statementType 固定为事实，因为只比较已提取数字。
 */
@Value
@Builder
public class ArticulationCheck {
    String name;
    String status;
    String detail;
    String statementType;
}

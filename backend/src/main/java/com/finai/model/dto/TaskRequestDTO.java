package com.finai.model.dto;

import com.finai.model.AnalysisTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskRequestDTO {

    /**
     * 公司代码
     */
    private String companyCode;

    /**
     * 公司名称
     */
    private String companyName;

    /**
     * 报告期
     */
    private String reportPeriod;

    /**
     * 分析类型
     */
    private AnalysisTask.AnalysisType analysisType;

    /**
     * 估值基准日(可选)
     */
    private String valuationDate;
}

package com.finai.service;

import com.finai.model.dto.RiskAlertDTO;
import com.finai.model.dto.RiskAssessmentDTO;

import java.util.List;

/**
 * 风险预警服务
 * 多维度风险检测与预警
 */
public interface RiskWarningService {

    /**
     * 执行风险检测
     * @param taskId 任务ID
     * @return 风险预警列表
     */
    List<RiskAlertDTO> detectRisks(String taskId);

    /**
     * 获取风险评估报告
     * @param taskId 任务ID
     * @return 风险评估报告
     */
    RiskAssessmentDTO getRiskAssessment(String taskId);

    /**
     * 检测财务造假风险
     * @param taskId 任务ID
     * @return 造假风险信号
     */
    List<RiskAlertDTO> detectFraudRisks(String taskId);

    /**
     * 检测流动性风险
     * @param taskId 任务ID
     * @return 流动性风险信号
     */
    List<RiskAlertDTO> detectLiquidityRisks(String taskId);

    /**
     * 检测经营风险
     * @param taskId 任务ID
     * @return 经营风险信号
     */
    List<RiskAlertDTO> detectOperationalRisks(String taskId);

    /**
     * 检测市场风险
     * @param taskId 任务ID
     * @return 市场风险信号
     */
    List<RiskAlertDTO> detectMarketRisks(String taskId);
}

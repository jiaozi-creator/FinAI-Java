package com.finai.service;

import com.finai.model.dto.ForecastResultDTO;

import java.util.List;

/**
 * 时间序列预测服务
 * 基于历史数据预测未来财务指标
 */
public interface ForecastService {

    /**
     * 预测财务指标
     * @param taskId 任务ID
     * @param periods 预测期数
     * @return 预测结果
     */
    ForecastResultDTO forecastMetrics(String taskId, int periods);

    /**
     * 预测特定指标
     * @param taskId 任务ID
     * @param metricName 指标名称
     * @param periods 预测期数
     * @return 预测结果
     */
    ForecastResultDTO forecastSpecificMetric(String taskId, String metricName, int periods);

    /**
     * 获取历史趋势分析
     * @param taskId 任务ID
     * @return 趋势分析列表
     */
    List<ForecastResultDTO.TrendAnalysis> analyzeTrends(String taskId);

    /**
     * 场景分析（乐观、中性、悲观）
     * @param taskId 任务ID
     * @param periods 预测期数
     * @return 多场景预测结果
     */
    ForecastResultDTO scenarioAnalysis(String taskId, int periods);
}

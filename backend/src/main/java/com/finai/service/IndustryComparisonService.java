package com.finai.service;

import com.finai.model.dto.ComparisonReportDTO;
import com.finai.model.dto.IndustryRankingDTO;

import java.util.List;

/**
 * 行业对比分析服务
 */
public interface IndustryComparisonService {

    /**
     * 与同行业公司对比
     * @param taskId 任务ID
     * @param peerCodes 对比公司代码列表
     * @return 对比报告
     */
    ComparisonReportDTO compareWithPeers(String taskId, List<String> peerCodes);

    /**
     * 获取行业排名
     * @param taskId 任务ID
     * @param industry 行业代码
     * @return 行业排名报告
     */
    IndustryRankingDTO getIndustryRanking(String taskId, String industry);

    /**
     * 获取建议的对比公司
     * @param taskId 任务ID
     * @return 建议的对比公司列表
     */
    List<String> getSuggestedPeers(String taskId);

    /**
     * 行业平均值对比
     * @param taskId 任务ID
     * @param industry 行业代码
     * @return 与行业平均值的对比
     */
    ComparisonReportDTO compareWithIndustryAverage(String taskId, String industry);
}

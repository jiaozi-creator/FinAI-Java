package com.finai.service.impl;

import com.finai.exception.ResourceNotFoundException;
import com.finai.model.dto.ComparisonReportDTO;
import com.finai.model.dto.IndustryRankingDTO;
import com.finai.repository.AnalysisTaskRepository;
import com.finai.service.IndustryComparisonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 没有可比公司财报和市价，不输出对比数字。
 */
@Service
@RequiredArgsConstructor
public class IndustryComparisonServiceImpl implements IndustryComparisonService {

    private final AnalysisTaskRepository taskRepository;

    private IllegalStateException unavailable(String taskId) {
        if (!taskRepository.existsById(taskId)) {
            throw new ResourceNotFoundException("Task not found: " + taskId);
        }
        return new IllegalStateException("同行业可比数据尚未核验，当前不能生成对比或排名");
    }

    @Override
    public ComparisonReportDTO compareWithPeers(String taskId, List<String> peerCodes) {
        throw unavailable(taskId);
    }

    @Override
    public IndustryRankingDTO getIndustryRanking(String taskId, String industry) {
        throw unavailable(taskId);
    }

    @Override
    public List<String> getSuggestedPeers(String taskId) {
        throw unavailable(taskId);
    }

    @Override
    public ComparisonReportDTO compareWithIndustryAverage(String taskId, String industry) {
        throw unavailable(taskId);
    }
}

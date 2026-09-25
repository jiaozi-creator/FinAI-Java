package com.finai.service.impl;

import com.finai.model.AnalysisTask;
import com.finai.model.dto.ForecastResultDTO;
import com.finai.repository.AnalysisTaskRepository;
import com.finai.service.ForecastService;
import com.finai.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预测不在选题 2 / 选题 4 的计算链里。这里不返回写死的趋势。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ForecastServiceImpl implements ForecastService {

    private static final String NOT_COMPUTED = "未接入财报时间序列，本次不输出预测值";

    private final AnalysisTaskRepository taskRepository;

    @Override
    public ForecastResultDTO forecastMetrics(String taskId, int periods) {
        return empty(taskId, periods);
    }

    @Override
    public ForecastResultDTO forecastSpecificMetric(String taskId, String metricName, int periods) {
        return empty(taskId, periods);
    }

    @Override
    public List<ForecastResultDTO.TrendAnalysis> analyzeTrends(String taskId) {
        requireTask(taskId);
        return List.of();
    }

    @Override
    public ForecastResultDTO scenarioAnalysis(String taskId, int periods) {
        return empty(taskId, periods);
    }

    private ForecastResultDTO empty(String taskId, int periods) {
        AnalysisTask task = requireTask(taskId);
        log.info("Forecast skipped for task {}", taskId);
        return ForecastResultDTO.builder()
                .forecastId(null)
                .taskId(taskId)
                .companyCode(task.getCompanyCode())
                .companyName(task.getCompanyName())
                .basePeriod(task.getReportPeriod())
                .periods(periods)
                .method("未计算")
                .metricForecasts(List.of())
                .trendAnalyses(List.of())
                .keyAssumptions(List.of(NOT_COMPUTED))
                .riskWarnings(List.of(NOT_COMPUTED))
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private AnalysisTask requireTask(String taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
    }
}

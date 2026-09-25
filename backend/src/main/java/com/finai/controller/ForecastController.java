package com.finai.controller;

import com.finai.model.dto.ForecastResultDTO;
import com.finai.service.ForecastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 时间序列预测控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
@Tag(name = "预测分析", description = "时间序列预测相关接口")
public class ForecastController {

    private final ForecastService forecastService;

    @GetMapping("/{taskId}")
    @Operation(summary = "预测财务指标", description = "基于历史数据预测未来财务指标")
    public ResponseEntity<ForecastResultDTO> forecastMetrics(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "3") int periods) {
        log.info("Forecasting metrics for task {}, periods: {}", taskId, periods);
        ForecastResultDTO result = forecastService.forecastMetrics(taskId, periods);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{taskId}/metric/{metricName}")
    @Operation(summary = "预测特定指标", description = "预测单个财务指标的未来值")
    public ResponseEntity<ForecastResultDTO> forecastSpecificMetric(
            @PathVariable String taskId,
            @PathVariable String metricName,
            @RequestParam(defaultValue = "3") int periods) {
        log.info("Forecasting metric {} for task {}", metricName, taskId);
        ForecastResultDTO result = forecastService.forecastSpecificMetric(taskId, metricName, periods);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{taskId}/trends")
    @Operation(summary = "趋势分析", description = "分析历史数据的趋势特征")
    public ResponseEntity<List<ForecastResultDTO.TrendAnalysis>> analyzeTrends(
            @PathVariable String taskId) {
        log.info("Analyzing trends for task: {}", taskId);
        List<ForecastResultDTO.TrendAnalysis> trends = forecastService.analyzeTrends(taskId);
        return ResponseEntity.ok(trends);
    }

    @GetMapping("/{taskId}/scenarios")
    @Operation(summary = "场景分析", description = "提供乐观、中性、悲观三种场景的预测")
    public ResponseEntity<ForecastResultDTO> scenarioAnalysis(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "3") int periods) {
        log.info("Performing scenario analysis for task: {}", taskId);
        ForecastResultDTO result = forecastService.scenarioAnalysis(taskId, periods);
        return ResponseEntity.ok(result);
    }
}

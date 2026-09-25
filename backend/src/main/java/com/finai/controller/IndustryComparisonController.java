package com.finai.controller;

import com.finai.model.dto.ComparisonReportDTO;
import com.finai.model.dto.IndustryRankingDTO;
import com.finai.service.IndustryComparisonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 行业对比分析控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/comparison")
@RequiredArgsConstructor
@Tag(name = "行业对比", description = "行业对比分析相关接口")
public class IndustryComparisonController {

    private final IndustryComparisonService comparisonService;

    @PostMapping("/{taskId}/peers")
    @Operation(summary = "与同行业公司对比", description = "将指定任务与选定的同行业公司进行对比")
    public ResponseEntity<ComparisonReportDTO> compareWithPeers(
            @PathVariable String taskId,
            @RequestBody List<String> peerCodes) {
        log.info("Comparing task {} with peers: {}", taskId, peerCodes);
        ComparisonReportDTO report = comparisonService.compareWithPeers(taskId, peerCodes);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/{taskId}/ranking")
    @Operation(summary = "获取行业排名", description = "获取公司在行业内的排名")
    public ResponseEntity<IndustryRankingDTO> getIndustryRanking(
            @PathVariable String taskId,
            @RequestParam String industry) {
        log.info("Getting industry ranking for task {}, industry: {}", taskId, industry);
        IndustryRankingDTO ranking = comparisonService.getIndustryRanking(taskId, industry);
        return ResponseEntity.ok(ranking);
    }

    @GetMapping("/{taskId}/suggested-peers")
    @Operation(summary = "获取建议的对比公司", description = "基于行业和规模推荐对比公司")
    public ResponseEntity<List<String>> getSuggestedPeers(@PathVariable String taskId) {
        log.info("Getting suggested peers for task: {}", taskId);
        List<String> peers = comparisonService.getSuggestedPeers(taskId);
        return ResponseEntity.ok(peers);
    }

    @GetMapping("/{taskId}/industry-average")
    @Operation(summary = "与行业平均值对比", description = "将公司指标与行业平均值进行对比")
    public ResponseEntity<ComparisonReportDTO> compareWithIndustryAverage(
            @PathVariable String taskId,
            @RequestParam String industry) {
        log.info("Comparing task {} with industry average", taskId);
        ComparisonReportDTO report = comparisonService.compareWithIndustryAverage(taskId, industry);
        return ResponseEntity.ok(report);
    }
}

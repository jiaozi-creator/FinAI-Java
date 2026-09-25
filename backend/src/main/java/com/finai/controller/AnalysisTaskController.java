package com.finai.controller;

import com.finai.model.AnalysisTask;
import com.finai.model.dto.*;
import com.finai.service.AnalysisService;
import com.finai.service.DemoTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 分析任务控制器
 *
 * 提供任务创建、查询、状态监控等API
 */
@Slf4j
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Tag(name = "Analysis Tasks", description = "分析任务管理API")
public class AnalysisTaskController {

    private final AnalysisService analysisService;
    private final DemoTaskService demoTaskService;

    @PostMapping("/demo")
    @Operation(summary = "打开内置样例", description = "用内置样例报表跑选题2和选题4，已完成则直接返回")
    public ResponseEntity<TaskResponseDTO> openDemo() {
        return ResponseEntity.ok(demoTaskService.open());
    }

    /**
     * 创建分析任务
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "创建分析任务", description = "创建新的财务分析任务")
    public ResponseEntity<TaskResponseDTO> createTask(
            @Parameter(description = "公司代码") @RequestParam String companyCode,
            @Parameter(description = "公司名称") @RequestParam String companyName,
            @Parameter(description = "报告期") @RequestParam String reportPeriod,
            @Parameter(description = "分析类型") @RequestParam(defaultValue = "FULL") AnalysisTask.AnalysisType analysisType,
            @Parameter(description = "上传的财报PDF(可选)") @RequestParam(required = false) MultipartFile file) {

        log.info("Creating analysis task: company={}, period={}, type={}",
                companyCode, reportPeriod, analysisType);

        TaskRequestDTO request = TaskRequestDTO.builder()
                .companyCode(companyCode)
                .companyName(companyName)
                .reportPeriod(reportPeriod)
                .analysisType(analysisType)
                .build();

        TaskResponseDTO response = analysisService.createTask(request, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "获取任务详情", description = "根据任务ID获取任务详细信息")
    public ResponseEntity<TaskResponseDTO> getTask(
            @Parameter(description = "任务ID") @PathVariable String taskId) {

        log.info("Getting task: {}", taskId);
        TaskResponseDTO response = analysisService.getTask(taskId);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取任务列表
     */
    @GetMapping
    @Operation(summary = "获取任务列表", description = "获取所有分析任务列表")
    public ResponseEntity<List<TaskResponseDTO>> listTasks(
            @Parameter(description = "公司代码(可选)") @RequestParam(required = false) String companyCode,
            @Parameter(description = "任务状态(可选)") @RequestParam(required = false) AnalysisTask.TaskStatus status,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {

        log.info("Listing tasks: company={}, status={}, page={}, size={}",
                companyCode, status, page, size);
        List<TaskResponseDTO> tasks = analysisService.listTasks(companyCode, status, page, size);
        return ResponseEntity.ok(tasks);
    }

    /**
     * 获取任务报告
     */
    @GetMapping("/{taskId}/report")
    @Operation(summary = "获取任务报告", description = "获取分析任务生成的报告")
    public ResponseEntity<AnalysisReportDTO> getReport(
            @Parameter(description = "任务ID") @PathVariable String taskId) {

        log.info("Getting report for task: {}", taskId);
        AnalysisReportDTO report = analysisService.getReport(taskId);
        return ResponseEntity.ok(report);
    }

    /**
     * 获取财务指标
     */
    @GetMapping("/{taskId}/metrics")
    @Operation(summary = "获取财务指标", description = "获取任务计算的财务指标")
    public ResponseEntity<FinancialMetricsDTO> getMetrics(
            @Parameter(description = "任务ID") @PathVariable String taskId) {

        log.info("Getting metrics for task: {}", taskId);
        FinancialMetricsDTO metrics = analysisService.getMetrics(taskId);
        return ResponseEntity.ok(metrics);
    }

    /**
     * 获取异常信号
     */
    @GetMapping("/{taskId}/anomalies")
    @Operation(summary = "获取异常信号", description = "获取检测到的财务异常信号")
    public ResponseEntity<List<AnomalySignalDTO>> getAnomalies(
            @Parameter(description = "任务ID") @PathVariable String taskId) {

        log.info("Getting anomalies for task: {}", taskId);
        List<AnomalySignalDTO> anomalies = analysisService.getAnomalies(taskId);
        return ResponseEntity.ok(anomalies);
    }

    /**
     * 获取估值结果
     */
    @GetMapping("/{taskId}/valuation")
    @Operation(summary = "获取估值结果", description = "获取任务的估值结果")
    public ResponseEntity<ValuationResultDTO> getValuation(
            @Parameter(description = "任务ID") @PathVariable String taskId) {

        log.info("Getting valuation for task: {}", taskId);
        ValuationResultDTO valuation = analysisService.getValuation(taskId);
        return ResponseEntity.ok(valuation);
    }

    /**
     * 获取证据列表
     */
    @GetMapping("/{taskId}/evidence")
    @Operation(summary = "获取证据列表", description = "获取任务提取的所有证据")
    public ResponseEntity<List<EvidenceDTO>> getEvidence(
            @Parameter(description = "任务ID") @PathVariable String taskId,
            @Parameter(description = "字段ID(可选)") @RequestParam(required = false) String fieldId) {

        log.info("Getting evidence for task: {}, fieldId={}", taskId, fieldId);
        List<EvidenceDTO> evidence = analysisService.getEvidence(taskId, fieldId);
        return ResponseEntity.ok(evidence);
    }

    /**
     * 取消任务
     */
    @PostMapping("/{taskId}/rerun")
    @Operation(summary = "重跑任务", description = "用已上传的文件重新抽取和计算")
    public ResponseEntity<Void> rerun(@PathVariable String taskId) {
        log.info("Rerunning task: {}", taskId);
        analysisService.executeTask(taskId);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{taskId}/cancel")
    @Operation(summary = "取消任务", description = "取消正在运行的任务")
    public ResponseEntity<Void> cancelTask(
            @Parameter(description = "任务ID") @PathVariable String taskId) {

        log.info("Cancelling task: {}", taskId);
        analysisService.cancelTask(taskId);
        return ResponseEntity.ok().build();
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{taskId}")
    @Operation(summary = "删除任务", description = "删除任务及其相关数据")
    public ResponseEntity<Void> deleteTask(
            @Parameter(description = "任务ID") @PathVariable String taskId) {

        log.info("Deleting task: {}", taskId);
        analysisService.deleteTask(taskId);
        return ResponseEntity.noContent().build();
    }
}

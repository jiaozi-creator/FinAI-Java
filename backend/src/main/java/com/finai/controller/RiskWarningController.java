package com.finai.controller;

import com.finai.model.dto.RiskAlertDTO;
import com.finai.model.dto.RiskAssessmentDTO;
import com.finai.service.RiskWarningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 风险预警控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/risks")
@RequiredArgsConstructor
@Tag(name = "风险预警", description = "风险检测与预警相关接口")
public class RiskWarningController {

    private final RiskWarningService riskWarningService;

    @GetMapping("/{taskId}")
    @Operation(summary = "检测风险", description = "检测指定任务的所有风险")
    public ResponseEntity<List<RiskAlertDTO>> detectRisks(@PathVariable String taskId) {
        log.info("Detecting risks for task: {}", taskId);
        List<RiskAlertDTO> risks = riskWarningService.detectRisks(taskId);
        return ResponseEntity.ok(risks);
    }

    @GetMapping("/{taskId}/assessment")
    @Operation(summary = "获取风险评估报告", description = "获取完整的风险评估报告")
    public ResponseEntity<RiskAssessmentDTO> getRiskAssessment(@PathVariable String taskId) {
        log.info("Getting risk assessment for task: {}", taskId);
        RiskAssessmentDTO assessment = riskWarningService.getRiskAssessment(taskId);
        return ResponseEntity.ok(assessment);
    }

    @GetMapping("/{taskId}/fraud")
    @Operation(summary = "检测财务造假风险", description = "检测财务造假相关风险信号")
    public ResponseEntity<List<RiskAlertDTO>> detectFraudRisks(@PathVariable String taskId) {
        log.info("Detecting fraud risks for task: {}", taskId);
        List<RiskAlertDTO> fraudRisks = riskWarningService.detectFraudRisks(taskId);
        return ResponseEntity.ok(fraudRisks);
    }

    @GetMapping("/{taskId}/liquidity")
    @Operation(summary = "检测流动性风险", description = "检测流动性相关风险信号")
    public ResponseEntity<List<RiskAlertDTO>> detectLiquidityRisks(@PathVariable String taskId) {
        log.info("Detecting liquidity risks for task: {}", taskId);
        List<RiskAlertDTO> liquidityRisks = riskWarningService.detectLiquidityRisks(taskId);
        return ResponseEntity.ok(liquidityRisks);
    }

    @GetMapping("/{taskId}/operational")
    @Operation(summary = "检测经营风险", description = "检测经营相关风险信号")
    public ResponseEntity<List<RiskAlertDTO>> detectOperationalRisks(@PathVariable String taskId) {
        log.info("Detecting operational risks for task: {}", taskId);
        List<RiskAlertDTO> operationalRisks = riskWarningService.detectOperationalRisks(taskId);
        return ResponseEntity.ok(operationalRisks);
    }

    @GetMapping("/{taskId}/market")
    @Operation(summary = "检测市场风险", description = "检测市场相关风险信号")
    public ResponseEntity<List<RiskAlertDTO>> detectMarketRisks(@PathVariable String taskId) {
        log.info("Detecting market risks for task: {}", taskId);
        List<RiskAlertDTO> marketRisks = riskWarningService.detectMarketRisks(taskId);
        return ResponseEntity.ok(marketRisks);
    }
}

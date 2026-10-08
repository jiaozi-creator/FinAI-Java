package com.finai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finai.analysis.ValuationParameters;
import com.finai.model.ValuationConfirmation;
import com.finai.repository.AnalysisTaskRepository;
import com.finai.repository.ValuationConfirmationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ValuationInputService {
    private final AnalysisTaskRepository tasks;
    private final ValuationConfirmationRepository confirmations;
    private final ObjectMapper mapper;

    public record ConfirmationRequest(ValuationParameters parameters, String confirmedBy, String rationale) {}

    public ValuationConfirmation latest(String taskId) {
        if (!tasks.existsById(taskId)) throw new com.finai.exception.ResourceNotFoundException("Task not found");
        return confirmations.findFirstByTaskIdOrderByConfirmedAtDesc(taskId).orElse(null);
    }

    public ValuationParameters parameters(ValuationConfirmation record) {
        if (record == null) return ValuationParameters.builder().build();
        try { return mapper.readValue(record.getParametersJson(), ValuationParameters.class); }
        catch (Exception e) { throw new IllegalStateException("Invalid valuation confirmation", e); }
    }

    @Transactional
    public ValuationConfirmation confirm(String taskId, ConfirmationRequest request) {
        var task = tasks.findLocked(taskId).orElseThrow(() -> new com.finai.exception.ResourceNotFoundException("Task not found"));
        if (task.getStatus() == com.finai.model.AnalysisTask.TaskStatus.RUNNING || task.getStatus() == com.finai.model.AnalysisTask.TaskStatus.QUEUED)
            throw new IllegalArgumentException("运行中不能修改估值确认，请等待本次运行结束");
        ValuationParameters p = request.parameters();
        if (p == null || !p.isAssumptionsConfirmed() || !p.isBridgePolicyConfirmed() || blank(p.getBridgePolicy())
                || blank(request.confirmedBy()) || blank(request.rationale())) throw new IllegalArgumentException("须明确确认全部参数、桥接政策、确认人及依据");
        if (p.getConfirmedFcff() == null || p.getConfirmedFcff().signum() <= 0 || p.getConfirmedGrowth() == null
                || p.getConfirmedGrowth().abs().compareTo(new java.math.BigDecimal("0.30")) > 0
                || p.getWacc() == null || p.getWacc().signum() <= 0 || p.getTerminalGrowth() == null
                || p.getTerminalGrowth().compareTo(new java.math.BigDecimal("-1")) <= 0
                || p.getWacc().compareTo(p.getTerminalGrowth()) <= 0 || p.getYears() < 1 || p.getYears() > 30
                || p.getUsableCash() == null || p.getUsableCash().signum() < 0
                || p.getGrossDebt() == null || p.getGrossDebt().signum() < 0
                || p.getMinorityAdjustment() == null || p.getMinorityAdjustment().signum() < 0
                || p.getNonCoreAssets() == null || p.getNonCoreAssets().signum() < 0
                || p.getDilutedShares() == null || p.getDilutedShares().signum() <= 0)
            throw new IllegalArgumentException("估值输入不完整或超出模型边界；金额统一为元、股数为股、比率为小数");
        String id = UUID.randomUUID().toString();
        try {
            var versioned = ValuationParameters.builder().wacc(p.getWacc()).terminalGrowth(p.getTerminalGrowth()).years(p.getYears())
                    .confirmedGrowth(p.getConfirmedGrowth()).confirmedFcff(p.getConfirmedFcff()).assumptionsConfirmed(true)
                    .bridgePolicyConfirmed(true).bridgePolicy(p.getBridgePolicy()).usableCash(p.getUsableCash()).grossDebt(p.getGrossDebt())
                    .minorityAdjustment(p.getMinorityAdjustment()).nonCoreAssets(p.getNonCoreAssets()).dilutedShares(p.getDilutedShares()).version(id).build();
            return confirmations.save(ValuationConfirmation.builder().id(id).taskId(taskId).confirmedBy(request.confirmedBy())
                    .confirmedAt(LocalDateTime.now()).source("user_confirmed").rationale(request.rationale())
                    .parametersJson(mapper.writeValueAsString(versioned)).build());
        } catch (java.io.IOException e) { throw new IllegalStateException("Could not persist valuation inputs", e); }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}

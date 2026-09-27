package com.finai.analysis;

import com.finai.config.LlmRuntime;
import com.finai.model.AnalysisTask;
import com.finai.model.dto.AnalysisReportDTO;
import com.finai.model.dto.AnomalySignalDTO;
import com.finai.model.dto.FinancialMetricsDTO;
import com.finai.model.dto.ValuationResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ReportComposer {

    private final SkillNarrator skillNarrator;
    private final LlmRuntime llmRuntime;

    public AnalysisReportDTO compose(AnalysisTask task,
                                     StatementExtract extract,
                                     FinancialMetricsDTO metrics,
                                     List<ArticulationCheck> checks,
                                     List<AnomalySignalDTO> anomalies,
                                     ValuationResultDTO valuation,
                                     long startedAtMs) {
        String growth = growthText(metrics);
        String profit = profitText(metrics);
        String cash = cashText(metrics, anomalies);
        String overall = overallText(checks, anomalies, valuation);
        SkillNarrator.Result narrative = skillNarrator.narrate(task, growth, profit, cash, overall, metrics, checks, anomalies, valuation, extract);
        List<AnalysisReportDTO.RiskFactor> risks = new ArrayList<>();
        for (AnomalySignalDTO anomaly : anomalies) {
            risks.add(AnalysisReportDTO.RiskFactor.builder()
                    .category(anomaly.getName())
                    .description(anomaly.getDescription())
                    .severity(anomaly.getSeverity() == null ? null : anomaly.getSeverity().name())
                    .evidence(anomaly.getAffectedMetrics())
                    .build());
        }
        List<AnalysisReportDTO.Citation> citations = extract.getLines().stream()
                .map(line -> AnalysisReportDTO.Citation.builder()
                        .sourceFile(line.getSourceFile())
                        .page(line.getPage())
                        .tableId(line.getFieldId())
                        .description(line.getFieldName() + " " + line.getSnippet())
                        .build())
                .toList();
        String conclusion = conclusion(metrics, valuation, narrative.llmUsed());
        if ("DEMO01".equals(task.getCompanyCode())) {
            conclusion = "事实：本任务的底稿是系统内置样例报表，不是上市公司真实年报。" + conclusion;
        }
        return AnalysisReportDTO.builder()
                .taskId(task.getTaskId())
                .title(task.getCompanyName() + " " + task.getReportPeriod() + " 财务分析与估值")
                .companyOverview(AnalysisReportDTO.CompanyOverview.builder()
                        .companyCode(task.getCompanyCode())
                        .companyName(task.getCompanyName())
                        .reportPeriod(task.getReportPeriod())
                        .reportType(task.getAnalysisType().name())
                        .build())
                .metrics(metrics)
                .anomalies(anomalies)
                .valuation(valuation)
                .articulationChecks(checks.stream().map(check -> AnalysisReportDTO.ArticulationItem.builder()
                        .name(check.getName())
                        .status(check.getStatus())
                        .detail(check.getDetail())
                        .statementType(check.getStatementType())
                        .build()).toList())
                .qualityAnalysis(AnalysisReportDTO.FinancialQualityAnalysis.builder()
                        .growthAnalysis(narrative.growth())
                        .profitabilityAnalysis(narrative.profit())
                        .cashFlowAnalysis(narrative.cash())
                        .overallAssessment(narrative.overall())
                        .build())
                .risks(risks)
                .citations(citations)
                .conclusion(conclusion)
                .executionSummary(AnalysisReportDTO.ExecutionSummary.builder()
                        .llmModel(narrative.llmCalls() > 0 ? llmRuntime.getModel() : "未调用")
                        .llmCallCount(narrative.llmCalls())
                        .temperature(llmRuntime.getTemperature())
                        .leavesMachine(narrative.llmCalls() > 0 && llmRuntime.leavesMachine())
                        .executionTimeMs(System.currentTimeMillis() - startedAtMs)
                        .dataVersion(AnomalyRuleEngine.RULE_VERSION)
                        .configVersion(AnalysisConfig.get().configVersion())
                        .promptVersion(narrative.promptVersion())
                        .skillVersion(narrative.skillVersion())
                        .fileSha256(extract.getSha256())
                        .toolsUsed(List.of("file.access", "pdf.parse", "metric.calculate", "articulation.check", "anomaly.rules", "valuation.dcf"))
                        .generatedAt(LocalDateTime.now().toString())
                        .build())
                .build();
    }

    private String conclusion(FinancialMetricsDTO metrics, ValuationResultDTO valuation, boolean llmUsed) {
        StringBuilder text = new StringBuilder();
        text.append("事实：指标和勾稽只使用财报抽到的列示数，单位是 ").append(metrics.getUnit()).append("。");
        if (metrics.getQoqGrowth() == null) {
            text.append("只有一份报告，环比未计算。");
        }
        text.append("推论：异常来自规则 ").append(AnomalyRuleEngine.RULE_VERSION).append("。");
        if (valuation == null || valuation.getValuationRange() == null) {
            text.append("估值区间未输出。");
        } else {
            text.append("估值区间是折现结果，依赖假设表，不是市价。");
        }
        text.append(llmUsed ? "观点：质量分析里的模型段落需要人工复核。" : "观点：本次没有可用的模型表述。");
        text.append("以上不构成投资建议。");
        return text.toString();
    }

    private String growthText(FinancialMetricsDTO metrics) {
        BigDecimal revenueGrowth = metrics.getYoyGrowth() == null ? null : metrics.getYoyGrowth().getRevenueGrowth();
        BigDecimal profitGrowth = metrics.getYoyGrowth() == null ? null : metrics.getYoyGrowth().getNetProfitGrowth();
        if (revenueGrowth == null && profitGrowth == null) {
            return "事实：没有同时抽到本期和上期的收入或归母净利润，同比未计算。";
        }
        return "事实：营业收入同比 " + pct(revenueGrowth) + "，归母净利润同比 " + pct(profitGrowth)
                + "。同比 = (本期-上期)/|上期|，上期来自同一行的对比列。";
    }

    private String profitText(FinancialMetricsDTO metrics) {
        FinancialMetricsDTO.FinancialRatios ratios = metrics.getRatios();
        if (ratios == null || (ratios.getGrossMargin() == null && ratios.getNetMargin() == null && ratios.getRoe() == null)) {
            return "事实：毛利率、净利率或 ROE 缺少分子或分母，未计算。";
        }
        return "事实：毛利率 " + pct(ratios.getGrossMargin()) + "，净利率 " + pct(ratios.getNetMargin())
                + "，ROE " + pct(ratios.getRoe()) + "。ROE 分母是期末权益，不是平均权益。";
    }

    private String cashText(FinancialMetricsDTO metrics, List<AnomalySignalDTO> anomalies) {
        FinancialMetricsDTO.FinancialRatios ratios = metrics.getRatios();
        String ratio = ratios == null ? "未提取" : pct(ratios.getOcfToNetProfit());
        boolean hit = anomalies.stream().anyMatch(item -> item.getType() == AnomalySignalDTO.AnomalyType.PROFIT_CASHFLOW_DIVERGENCE);
        return "事实：经营现金流/归母净利润 = " + ratio + "。"
                + (hit ? "推论：已触发利润与现金流背离规则。" : "推论：未触发利润与现金流背离规则。");
    }

    private String overallText(List<ArticulationCheck> checks, List<AnomalySignalDTO> anomalies, ValuationResultDTO valuation) {
        String check = checks.isEmpty() ? "勾稽未执行" : checks.get(0).getStatus() + " " + checks.get(0).getDetail();
        String range = valuation == null || valuation.getValuationRange() == null
                ? "估值区间未计算"
                : "估值中值 " + valuation.getValuationRange().getMid();
        return "事实：勾稽 " + check + "。异常 " + anomalies.size() + " 条。推论：" + range + "。";
    }

    private static String pct(BigDecimal value) {
        if (value == null) {
            return "未提取";
        }
        return value.multiply(new BigDecimal("100")).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + "%";
    }
}

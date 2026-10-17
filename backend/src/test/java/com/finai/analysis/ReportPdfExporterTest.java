package com.finai.analysis;

import com.finai.model.dto.AnalysisReportDTO;
import com.finai.model.dto.AnomalySignalDTO;
import com.finai.model.dto.FinancialMetricsDTO;
import com.finai.model.dto.ValuationResultDTO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ReportPdfExporterTest {

    @Test
    void snapshotBecomesPdfWithMetricsAndBlockedValuation() throws Exception {
        Path font = ReportPdfExporter.resolveFont(null);
        assumeTrue(font != null && Files.isRegularFile(font), "本机没有中文字体，跳过 PDF 导出");

        AnalysisReportDTO report = AnalysisReportDTO.builder()
                .taskId("TASK_PDF")
                .title("景行示范 2024A 分析报告")
                .businessStatus("NEEDS_REVIEW")
                .companyOverview(AnalysisReportDTO.CompanyOverview.builder()
                        .companyCode("DEMO01").companyName("景行示范").reportPeriod("2024A").reportType("完整分析").build())
                .conclusion("合成样本只验证导出，不是投资建议。扣非 −39.31。")
                .metrics(FinancialMetricsDTO.builder()
                        .period("2024A").unit("万元").scope("consolidated")
                        .coreMetrics(FinancialMetricsDTO.CoreMetrics.builder().revenue(new BigDecimal("10000")).build())
                        .yoyGrowth(FinancialMetricsDTO.GrowthRates.builder().revenueGrowth(new BigDecimal("0.25")).build())
                        .ratios(FinancialMetricsDTO.FinancialRatios.builder().grossMargin(new BigDecimal("0.4")).build())
                        .build())
                .anomalies(java.util.List.of(AnomalySignalDTO.builder()
                        .name("利润现金背离").severity(AnomalySignalDTO.Severity.MEDIUM)
                        .description("经营现金流低于归母净利润。").build()))
                .valuation(ValuationResultDTO.builder()
                        .businessStatus("INPUT_INCOMPLETE").blockReason("预测增速未确认").build())
                .build();

        byte[] pdf = new ReportPdfExporter(font.toString()).write(report);
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", "");
            assertThat(text).contains("景行示范").contains("10,000").contains("25.00%")
                    .contains("40.00%").contains("预测增速未确认").contains("利润现金背离")
                    .contains("-39.31");
        }
        assertThat(ReportPdfExporter.attachmentHeader(report))
                .contains("filename*=UTF-8''%E6%99%AF%E8%A1%8C%E7%A4%BA%E8%8C%83_2024A");
    }
}

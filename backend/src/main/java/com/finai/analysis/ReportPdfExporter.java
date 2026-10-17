package com.finai.analysis;

import com.finai.model.dto.AnalysisReportDTO;
import com.finai.model.dto.AnomalySignalDTO;
import com.finai.model.dto.FinancialMetricsDTO;
import com.finai.model.dto.ValuationResultDTO;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 把已保存的分析快照排成 PDF。导出不重新抽数，也不重新计算。
 */
@Component
public class ReportPdfExporter {

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 48f;
    private static final float BOTTOM = 52f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2;

    private final String configuredFont;

    public ReportPdfExporter(@Value("${finai.report.font-path:}") String configuredFont) {
        this.configuredFont = configuredFont;
    }

    public byte[] write(AnalysisReportDTO report) {
        return write(report, "TPL_STANDARD");
    }

    public byte[] write(AnalysisReportDTO report, String templateId) {
        Path fontPath = resolveFont(configuredFont);
        if (fontPath == null) {
            throw new IllegalStateException("没有找到中文字体。设置 REPORT_FONT_PATH 指向 .ttf 或 .ttc 后再导出。");
        }
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            TrueTypeCollection collection = null;
            Layout layout = null;
            try {
                if (fontPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ttc")) {
                    collection = openCollection(fontPath);
                }
                document.getDocumentInformation().setTitle(text(report.getTitle(), "FinAI 分析报告"));
                document.getDocumentInformation().setCreator("FinAI");
                layout = new Layout(document, loadFont(document, fontPath, collection));
                layout.render(report, templateId == null ? "TPL_STANDARD" : templateId);
                layout.finish();
                document.save(out);
            } finally {
                if (layout != null) {
                    layout.closeQuietly();
                }
                if (collection != null) {
                    collection.close();
                }
            }
            return out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("PDF 导出失败", exception);
        }
    }

    public static String attachmentHeader(AnalysisReportDTO report) {
        String company = report.getCompanyOverview() == null ? null : report.getCompanyOverview().getCompanyName();
        String period = report.getCompanyOverview() == null ? null : report.getCompanyOverview().getReportPeriod();
        String raw = text(company, text(report.getTaskId(), "report"))
                + (period == null || period.isBlank() ? "" : "_" + period)
                + "_分析报告.pdf";
        String filename = raw.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
        return ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString();
    }

    static Path resolveFont(String configured) {
        if (configured != null && !configured.isBlank()) {
            Path configuredPath = Path.of(configured);
            if (!Files.isRegularFile(configuredPath)) {
                throw new IllegalStateException("配置的中文字体不存在: " + configuredPath);
            }
            return configuredPath;
        }
        for (String candidate : List.of(
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/msyh.ttc",
                "C:/Windows/Fonts/simsun.ttc",
                "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc")) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    private static TrueTypeCollection openCollection(Path fontPath) throws IOException {
        return new TrueTypeCollection(fontPath.toFile());
    }

    private static PDFont loadFont(PDDocument document, Path fontPath, TrueTypeCollection collection) throws IOException {
        if (collection == null) {
            return PDType0Font.load(document, fontPath.toFile());
        }
        TrueTypeFont[] found = new TrueTypeFont[1];
        collection.processAllFonts(font -> {
            if (found[0] == null) {
                found[0] = font;
            }
        });
        if (found[0] == null) {
            throw new IOException("字体集合是空的: " + fontPath);
        }
        return PDType0Font.load(document, found[0], true);
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String amount(BigDecimal value) {
        if (value == null) {
            return "未计算";
        }
        return new DecimalFormat("#,##0.####").format(value);
    }

    private static String percent(BigDecimal value) {
        if (value == null) {
            return "未计算";
        }
        return new DecimalFormat("0.00%").format(value);
    }

    private static String times(BigDecimal value) {
        if (value == null) {
            return "未计算";
        }
        return new DecimalFormat("#,##0.00").format(value);
    }

    private static String scopeLabel(String scope) {
        if (scope == null || scope.isBlank()) {
            return "未标注";
        }
        return switch (scope) {
            case "consolidated" -> "合并";
            case "parent" -> "母公司";
            case "summary" -> "主要会计数据";
            default -> scope;
        };
    }

    private static String sourceLabel(String source) {
        if (source == null || source.isBlank()) {
            return "未标注";
        }
        return switch (source) {
            case "extract" -> "财报事实";
            case "formula" -> "公式计算";
            case "default_unconfirmed" -> "默认值，待确认";
            case "user_confirmed", "user" -> "用户已确认";
            case "llm_suggested", "llm_suggest" -> "模型建议，待确认";
            default -> source;
        };
    }

    private final class Layout {
        private final PDDocument document;
        private final PDFont font;
        private PDPageContentStream stream;
        private float y;
        private int pageNumber;

        private Layout(PDDocument document, PDFont font) throws IOException {
            this.document = document;
            this.font = font;
            openPage();
        }

        private void render(AnalysisReportDTO report, String templateId) throws IOException {
            boolean simple = "TPL_SIMPLE".equals(templateId);
            boolean investment = "TPL_INVESTMENT".equals(templateId);
            title(text(report.getTitle(), "FinAI 分析报告"));
            AnalysisReportDTO.CompanyOverview company = report.getCompanyOverview();
            if (company != null) {
                meta(company.getCompanyName() + "  " + text(company.getCompanyCode(), "")
                        + "  " + text(company.getReportPeriod(), "")
                        + "  " + text(company.getReportType(), ""));
            }
            if (report.getBusinessStatus() != null) {
                paragraph("报告状态：" + report.getBusinessStatus());
            }

            heading("结论");
            paragraph(text(report.getConclusion(), "本次没有书面结论。"));

            if (!investment) {
                heading("财务指标");
                metrics(report.getMetrics());
                heading("财务比率");
                ratios(report.getMetrics());
            }
            if (!simple && !investment) {
                heading("财务质量");
                quality(report.getQualityAnalysis());
                heading("勾稽");
                articulation(report.getArticulationChecks());
            }
            if (!investment) {
                heading("异常信号");
                anomalies(report.getAnomalies());
            }
            if (!simple) {
                heading("估值");
                valuation(report.getValuation());
                heading("主要风险");
                risks(report.getRisks());
            }
            heading("运行摘要");
            execution(report.getExecutionSummary());
            paragraph("本 PDF 只转写该任务已保存的快照。导出时不重新抽取财报，也不重新计算指标。未确认的估值参数不会在这里被补成区间。");
        }

        private void metrics(FinancialMetricsDTO metrics) throws IOException {
            if (metrics == null || metrics.getCoreMetrics() == null) {
                paragraph("未计算。");
                return;
            }
            paragraph("单位 " + text(metrics.getUnit(), "未标注") + "，范围 " + scopeLabel(metrics.getScope())
                    + "，期间 " + text(metrics.getPeriod(), "未标注"));
            FinancialMetricsDTO.CoreMetrics core = metrics.getCoreMetrics();
            FinancialMetricsDTO.GrowthRates growth = metrics.getYoyGrowth();
            float[] widths = {168f, 176f, 155f};
            row(new String[]{"指标", "本期", "同比"}, widths, true);
            List<MetricLine> lines = List.of(
                    withGrowth("营业收入", core.getRevenue(), growth == null ? null : growth.getRevenueGrowth()),
                    withGrowth("营业总收入", core.getRevenueTotal(), growth == null ? null : growth.getRevenueTotalGrowth()),
                    amountOnly("合并净利润", core.getNetProfitConsolidated()),
                    withGrowth("归母净利润", core.getNetProfit(), growth == null ? null : growth.getNetProfitGrowth()),
                    withGrowth("扣非归母净利润", core.getNetProfitDeducted(), growth == null ? null : growth.getNetProfitDeductedGrowth()),
                    withGrowth("经营现金流", core.getOperatingCashFlow(), growth == null ? null : growth.getOperatingCashFlowGrowth()),
                    amountOnly("资本开支代理值", core.getCapexProxy()),
                    withGrowth("简式自由现金流", core.getSimpleFreeCashFlow() == null ? core.getFreeCashFlow() : core.getSimpleFreeCashFlow(),
                            growth == null ? null : growth.getSimpleFreeCashFlowGrowth()),
                    withGrowth("资产总计", core.getTotalAssets(), growth == null ? null : growth.getTotalAssetsGrowth()),
                    withGrowth("负债合计", core.getTotalLiabilities(), growth == null ? null : growth.getTotalLiabilitiesGrowth()),
                    withGrowth("归母权益", core.getNetAssets(), growth == null ? null : growth.getNetAssetsGrowth()),
                    withGrowth("少数股东权益", core.getMinorityInterest(), growth == null ? null : growth.getMinorityInterestGrowth()),
                    amountOnly("货币资金", core.getCash()),
                    withGrowth("期末总股本", core.getSharesOutstanding(), growth == null ? null : growth.getSharesOutstandingGrowth()),
                    withGrowth("基本每股收益", core.getBasicEps(), growth == null ? null : growth.getBasicEpsGrowth())
            );
            for (MetricLine metric : lines) {
                String yoy = metric.growthShown() ? percent(metric.growth()) : "";
                row(new String[]{metric.label(), amount(metric.value()), yoy}, widths, false);
            }
        }

        private void ratios(FinancialMetricsDTO metrics) throws IOException {
            FinancialMetricsDTO.FinancialRatios ratios = metrics == null ? null : metrics.getRatios();
            if (ratios == null) {
                paragraph("未计算。");
                return;
            }
            float[] widths = {250f, 249f};
            row(new String[]{"比率", "数值"}, widths, true);
            row(new String[]{"毛利率", percent(ratios.getGrossMargin())}, widths, false);
            row(new String[]{"归母净利率", percent(ratios.getNetMargin())}, widths, false);
            row(new String[]{"合并净利率", percent(ratios.getConsolidatedNetMargin())}, widths, false);
            row(new String[]{"加权平均 ROE（披露）", percent(ratios.getDisclosedWeightedRoe())}, widths, false);
            row(new String[]{"平均归母权益 ROE", percent(ratios.getAverageEquityRoe())}, widths, false);
            row(new String[]{"ROA", percent(ratios.getRoa())}, widths, false);
            row(new String[]{"资产负债率", percent(ratios.getAssetLiabilityRatio())}, widths, false);
            row(new String[]{"流动比率", times(ratios.getCurrentRatio())}, widths, false);
            row(new String[]{"经营现金流/归母净利润", times(ratios.getOcfToNetProfit())}, widths, false);
        }

        private void quality(AnalysisReportDTO.FinancialQualityAnalysis quality) throws IOException {
            if (quality == null) {
                paragraph("未计算。");
                return;
            }
            paragraph("成长：" + text(quality.getGrowthAnalysis(), "未计算。"));
            paragraph("盈利：" + text(quality.getProfitabilityAnalysis(), "未计算。"));
            paragraph("现金流：" + text(quality.getCashFlowAnalysis(), "未计算。"));
            paragraph("综合：" + text(quality.getOverallAssessment(), "未计算。"));
        }

        private void articulation(List<AnalysisReportDTO.ArticulationItem> checks) throws IOException {
            if (checks == null || checks.isEmpty()) {
                paragraph("未做勾稽。");
                return;
            }
            for (AnalysisReportDTO.ArticulationItem check : checks) {
                paragraph(text(check.getStatus(), "未标注") + "  " + text(check.getName(), "勾稽")
                        + "  " + text(check.getDetail(), ""));
            }
        }

        private void anomalies(List<AnomalySignalDTO> anomalies) throws IOException {
            if (anomalies == null || anomalies.isEmpty()) {
                paragraph("没有异常信号。");
                return;
            }
            for (AnomalySignalDTO anomaly : anomalies) {
                paragraph(text(anomaly.getSeverity() == null ? null : anomaly.getSeverity().name(), "未分级")
                        + "  " + text(anomaly.getName(), "异常")
                        + (anomaly.getRuleTriggered() == null ? "" : "  " + anomaly.getRuleTriggered()));
                paragraph(text(anomaly.getDescription(), ""));
                if (anomaly.getRecommendation() != null && !anomaly.getRecommendation().isBlank()) {
                    paragraph("建议：" + anomaly.getRecommendation());
                }
            }
        }

        private void valuation(ValuationResultDTO valuation) throws IOException {
            if (valuation == null) {
                paragraph("未计算。");
                return;
            }
            paragraph("状态：" + text(valuation.getBusinessStatus(), "未标注"));
            if (valuation.getBlockReason() != null && !valuation.getBlockReason().isBlank()) {
                paragraph(valuation.getBlockReason());
            }
            ValuationResultDTO.ValuationRange range = valuation.getValuationRange();
            if (range == null || range.getMid() == null) {
                paragraph("估值区间：未输出。");
            } else {
                paragraph("估值区间：低 " + amount(range.getLow()) + "，中 " + amount(range.getMid()) + "，高 " + amount(range.getHigh()));
                if (range.getMethodology() != null) {
                    paragraph(range.getMethodology());
                }
                if (range.getApplicabilityNote() != null) {
                    paragraph(range.getApplicabilityNote());
                }
            }
            if (valuation.getAssumptions() == null || valuation.getAssumptions().isEmpty()) {
                return;
            }
            float[] widths = {110f, 120f, 269f};
            row(new String[]{"参数", "取值", "依据"}, widths, true);
            for (ValuationResultDTO.ValuationAssumption assumption : valuation.getAssumptions()) {
                row(new String[]{
                        text(assumption.getParameter(), ""),
                        text(assumption.getValue(), ""),
                        sourceLabel(assumption.getSource()) + " " + text(assumption.getRationale(), "")
                }, widths, false);
            }
        }

        private void risks(List<AnalysisReportDTO.RiskFactor> risks) throws IOException {
            if (risks == null || risks.isEmpty()) {
                paragraph("没有单独的风险条目。主链路里的异常规则见上一节。");
                return;
            }
            for (AnalysisReportDTO.RiskFactor risk : risks) {
                paragraph(text(risk.getSeverity(), "未分级") + "  " + text(risk.getCategory(), "风险"));
                paragraph(text(risk.getDescription(), ""));
            }
        }

        private void execution(AnalysisReportDTO.ExecutionSummary summary) throws IOException {
            if (summary == null) {
                paragraph("没有运行摘要。");
                return;
            }
            paragraph("模型：" + text(summary.getLlmModel(), "未记录")
                    + "，调用 " + (summary.getLlmCallCount() == null ? "未记录" : summary.getLlmCallCount())
                    + " 次，耗时 " + (summary.getExecutionTimeMs() == null ? "未记录" : summary.getExecutionTimeMs() + " 毫秒"));
            paragraph("生成时间：" + text(summary.getGeneratedAt(), "未记录"));
            paragraph("文件 SHA-256：" + text(summary.getFileSha256(), "未记录"));
        }

        private void title(String value) throws IOException {
            ensure(36f);
            draw(value, 18f, MARGIN, y, CONTENT_WIDTH);
            y -= 28f;
        }

        private void meta(String value) throws IOException {
            ensure(20f);
            draw(value, 10f, MARGIN, y, CONTENT_WIDTH);
            y -= 18f;
        }

        private void heading(String value) throws IOException {
            ensure(28f);
            y -= 8f;
            draw(value, 13f, MARGIN, y, CONTENT_WIDTH);
            y -= 8f;
            stream.setStrokingColor(0.75f);
            stream.moveTo(MARGIN, y);
            stream.lineTo(MARGIN + CONTENT_WIDTH, y);
            stream.stroke();
            y -= 16f;
        }

        private void paragraph(String value) throws IOException {
            for (String line : wrap(value, 10.5f, CONTENT_WIDTH)) {
                ensure(16f);
                draw(line, 10.5f, MARGIN, y, CONTENT_WIDTH);
                y -= 15f;
            }
            y -= 4f;
        }

        private void row(String[] cells, float[] widths, boolean header) throws IOException {
            List<List<String>> wrapped = new ArrayList<>();
            int maxLines = 1;
            for (int i = 0; i < cells.length; i++) {
                List<String> lines = wrap(cells[i] == null ? "" : cells[i], 9.5f, widths[i] - 8f);
                wrapped.add(lines);
                maxLines = Math.max(maxLines, lines.size());
            }
            float height = maxLines * 13f + 8f;
            ensure(height);
            if (header) {
                stream.setNonStrokingColor(0.93f, 0.94f, 0.92f);
                stream.addRect(MARGIN, y - height + 10f, CONTENT_WIDTH, height);
                stream.fill();
                stream.setNonStrokingColor(0f);
            }
            float x = MARGIN;
            for (int i = 0; i < cells.length; i++) {
                float lineY = y;
                for (String line : wrapped.get(i)) {
                    draw(line, 9.5f, x + 4f, lineY, widths[i] - 8f);
                    lineY -= 13f;
                }
                x += widths[i];
            }
            y -= height;
        }

        private void draw(String value, float size, float x, float baseline, float width) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, baseline);
            stream.showText(sanitize(value));
            stream.endText();
        }

        private String sanitize(String value) {
            if (value == null || value.isEmpty()) {
                return "";
            }
            StringBuilder out = new StringBuilder(value.length());
            for (int offset = 0; offset < value.length(); ) {
                int codePoint = value.codePointAt(offset);
                offset += Character.charCount(codePoint);
                if (codePoint == '\n') {
                    out.append('\n');
                    continue;
                }
                int mapped = switch (codePoint) {
                    case '\t', '\r', 0x00A0, 0x3000 -> ' ';
                    case 0x2212, 0x2010, 0x2011, 0x2012, 0x2013, 0x2014, 0x2015, 0xFE63, 0xFF0D -> '-';
                    case 0x2018, 0x2019 -> '\'';
                    case 0x201C, 0x201D -> '"';
                    default -> codePoint;
                };
                if (!encodable(mapped)) {
                    out.append('?');
                    continue;
                }
                out.appendCodePoint(mapped);
            }
            return out.toString();
        }

        private boolean encodable(int codePoint) {
            try {
                font.encode(new String(Character.toChars(codePoint)));
                return true;
            } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                return false;
            }
        }

        private List<String> wrap(String value, float size, float width) throws IOException {
            String source = sanitize(value);
            if (source.isBlank()) {
                source = " ";
            }
            source = source.replace("\r", "");
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            float used = 0f;
            for (int i = 0; i < source.length(); i++) {
                char ch = source.charAt(i);
                if (ch == '\n') {
                    lines.add(current.toString());
                    current.setLength(0);
                    used = 0f;
                    continue;
                }
                float glyph = glyphWidth(ch, size);
                if (used + glyph > width && !current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                    used = 0f;
                }
                current.append(ch);
                used += glyph;
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            if (lines.isEmpty()) {
                lines.add(" ");
            }
            return lines;
        }

        private float glyphWidth(char ch, float size) throws IOException {
            try {
                return font.getStringWidth(String.valueOf(ch)) / 1000f * size;
            } catch (IllegalArgumentException exception) {
                return size;
            }
        }

        private void ensure(float height) throws IOException {
            if (y - height < BOTTOM) {
                openPage();
            }
        }

        private void openPage() throws IOException {
            if (stream != null) {
                footer();
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            pageNumber++;
            y = PAGE_HEIGHT - MARGIN;
        }

        private void footer() throws IOException {
            stream.beginText();
            stream.setFont(font, 9f);
            stream.newLineAtOffset(MARGIN, 28f);
            stream.showText("FinAI 分析报告  第 " + pageNumber + " 页");
            stream.endText();
        }

        private void finish() throws IOException {
            if (stream != null) {
                footer();
                stream.close();
                stream = null;
            }
        }

        private void closeQuietly() {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ignored) {
                    stream = null;
                }
                stream = null;
            }
        }
    }

    private static MetricLine withGrowth(String label, BigDecimal value, BigDecimal growth) {
        return new MetricLine(label, value, growth, true);
    }

    private static MetricLine amountOnly(String label, BigDecimal value) {
        return new MetricLine(label, value, null, false);
    }

    private record MetricLine(String label, BigDecimal value, BigDecimal growth, boolean growthShown) {
    }
}

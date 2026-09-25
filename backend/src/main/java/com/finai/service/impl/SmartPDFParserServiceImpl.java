package com.finai.service.impl;

import com.finai.model.dto.PDFParseResultDTO;
import com.finai.service.SmartPDFParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF 智能解析服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartPDFParserServiceImpl implements SmartPDFParserService {

    @Override
    public PDFParseResultDTO parsePDF(MultipartFile file) {
        log.info("Parsing uploaded PDF file: {}", file.getOriginalFilename());

        try {
            // 保存临时文件
            String tempDir = "./data/temp";
            Files.createDirectories(Paths.get(tempDir));

            String fileName = file.getOriginalFilename();
            String filePath = tempDir + "/" + UUID.randomUUID() + "_" + fileName;
            file.transferTo(new File(filePath));

            // 解析文件
            return parsePDF(filePath);
        } catch (IOException e) {
            log.error("Failed to parse PDF file", e);
            return PDFParseResultDTO.builder()
                    .status(PDFParseResultDTO.ParseStatus.FAILED)
                    .errorMessage("文件解析失败: " + e.getMessage())
                    .parsedAt(LocalDateTime.now())
                    .build();
        }
    }

    @Override
    public PDFParseResultDTO parsePDF(String filePath) {
        log.info("Parsing PDF file from path: {}", filePath);

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            String fileName = Paths.get(filePath).getFileName().toString();
            int totalPages = document.getNumberOfPages();

            // 提取文本
            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(document);

            // 识别报表类型
            PDFParseResultDTO.ReportType reportType = identifyReportType(filePath);

            // 提取公司信息
            PDFParseResultDTO.CompanyInfo companyInfo = extractCompanyInfo(fullText);

            // 提取报告期
            String reportPeriod = extractReportPeriod(fullText);

            // 提取表格
            List<PDFParseResultDTO.TableData> tables = extractTables(filePath);

            // 提取图表
            List<PDFParseResultDTO.ChartData> charts = extractCharts(filePath);

            // 提取财务指标
            Map<String, Object> financialMetrics = extractFinancialMetrics(filePath);

            // 提取附注
            List<PDFParseResultDTO.NoteInfo> notes = extractNotes(filePath);

            // 构建文本块
            List<PDFParseResultDTO.TextBlock> textBlocks = buildTextBlocks(document);

            return PDFParseResultDTO.builder()
                    .parseId(UUID.randomUUID().toString())
                    .fileName(fileName)
                    .filePath(filePath)
                    .totalPages(totalPages)
                    .reportType(reportType)
                    .companyInfo(companyInfo)
                    .reportPeriod(reportPeriod)
                    .tables(tables)
                    .charts(charts)
                    .financialMetrics(financialMetrics)
                    .textBlocks(textBlocks)
                    .notes(notes)
                    .status(PDFParseResultDTO.ParseStatus.SUCCESS)
                    .parsedAt(LocalDateTime.now())
                    .confidence(0.85)
                    .build();

        } catch (IOException e) {
            log.error("Failed to parse PDF: {}", filePath, e);
            return PDFParseResultDTO.builder()
                    .fileName(Paths.get(filePath).getFileName().toString())
                    .filePath(filePath)
                    .status(PDFParseResultDTO.ParseStatus.FAILED)
                    .errorMessage("解析失败: " + e.getMessage())
                    .parsedAt(LocalDateTime.now())
                    .build();
        }
    }

    @Override
    public List<PDFParseResultDTO.TableData> extractTables(String filePath) {
        log.info("Extracting tables from PDF: {}", filePath);

        List<PDFParseResultDTO.TableData> tables = new ArrayList<>();

        // 主表行项目由 StatementExtractor 在任务流水线里抽取，这里不再返回写死的样例表。
        return tables;
    }

    @Override
    public List<PDFParseResultDTO.ChartData> extractCharts(String filePath) {
        log.info("Extracting charts from PDF: {}", filePath);

        List<PDFParseResultDTO.ChartData> charts = new ArrayList<>();

        // TODO: 实现图表提取逻辑
        // 1. 识别图片区域
        // 2. 提取图片
        // 3. OCR 识别图表中的文字和数据

        return charts;
    }

    @Override
    public Map<String, Object> extractFinancialMetrics(String filePath) {
        log.info("Extracting financial metrics from PDF: {}", filePath);

        Map<String, Object> metrics = new HashMap<>();

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            // TODO: 使用正则表达式或 NLP 提取财务指标
            // 示例提取逻辑
            metrics.putAll(extractMetricsFromText(text));

        } catch (IOException e) {
            log.error("Failed to extract metrics", e);
        }

        return metrics;
    }

    @Override
    public PDFParseResultDTO.AlignedData alignMultiYearReports(List<String> filePaths) {
        log.info("Aligning multi-year reports: {}", filePaths);

        List<String> years = new ArrayList<>();
        Map<String, List<Object>> alignedMetrics = new HashMap<>();
        Map<String, List<Boolean>> missingDataFlags = new HashMap<>();
        Map<String, List<String>> dataSources = new HashMap<>();

        // 解析每个年度的报表
        for (String filePath : filePaths) {
            PDFParseResultDTO parseResult = parsePDF(filePath);
            String year = parseResult.getReportPeriod();
            years.add(year);

            // 对齐财务指标
            Map<String, Object> metrics = parseResult.getFinancialMetrics();
            for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                String metricName = entry.getKey();
                Object value = entry.getValue();

                alignedMetrics.computeIfAbsent(metricName, k -> new ArrayList<>()).add(value);
                missingDataFlags.computeIfAbsent(metricName, k -> new ArrayList<>()).add(value == null);
                dataSources.computeIfAbsent(metricName, k -> new ArrayList<>()).add(filePath);
            }
        }

        return PDFParseResultDTO.AlignedData.builder()
                .years(years)
                .alignedMetrics(alignedMetrics)
                .missingDataFlags(missingDataFlags)
                .dataSources(dataSources)
                .build();
    }

    @Override
    public PDFParseResultDTO.TableData ocrTable(String imagePath) {
        log.info("OCR table from image: {}", imagePath);

        // TODO: 实现 OCR 识别
        // 可以使用 Tesseract OCR 或云端 OCR 服务

        return PDFParseResultDTO.TableData.builder()
                .tableId(UUID.randomUUID().toString())
                .title("OCR识别的表格")
                .confidence(0.75)
                .build();
    }

    @Override
    public PDFParseResultDTO.ReportType identifyReportType(String filePath) {
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(5); // 只读前5页
            String text = stripper.getText(document);

            if (text.contains("年度报告") || text.contains("Annual Report")) {
                return PDFParseResultDTO.ReportType.ANNUAL_REPORT;
            } else if (text.contains("半年度报告") || text.contains("Semi-annual Report")) {
                return PDFParseResultDTO.ReportType.SEMI_ANNUAL_REPORT;
            } else if (text.contains("季度报告") || text.contains("Quarterly Report")) {
                return PDFParseResultDTO.ReportType.QUARTERLY_REPORT;
            } else if (text.contains("招股说明书") || text.contains("Prospectus")) {
                return PDFParseResultDTO.ReportType.PROSPECTUS;
            }
        } catch (IOException e) {
            log.error("Failed to identify report type", e);
        }

        return PDFParseResultDTO.ReportType.OTHER;
    }

    @Override
    public List<PDFParseResultDTO.NoteInfo> extractNotes(String filePath) {
        log.info("Extracting notes from PDF: {}", filePath);

        List<PDFParseResultDTO.NoteInfo> notes = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            // TODO: 使用正则表达式识别附注
            // 附注通常格式为 "附注X：标题"

            Pattern notePattern = Pattern.compile("附注\\s*(\\d+)[：:](.*?)(?=附注|$)", Pattern.DOTALL);
            Matcher matcher = notePattern.matcher(text);

            while (matcher.find()) {
                String noteNumber = matcher.group(1);
                String content = matcher.group(2).trim();

                // 提取标题（第一行）
                String[] lines = content.split("\\n", 2);
                String title = lines.length > 0 ? lines[0].trim() : "";
                String noteContent = lines.length > 1 ? lines[1].trim() : content;

                notes.add(PDFParseResultDTO.NoteInfo.builder()
                        .noteNumber(noteNumber)
                        .title(title)
                        .content(noteContent)
                        .build());
            }

        } catch (IOException e) {
            log.error("Failed to extract notes", e);
        }

        return notes;
    }

    /**
     * 提取公司信息
     */
    private PDFParseResultDTO.CompanyInfo extractCompanyInfo(String text) {
        // TODO: 使用正则表达式或 NLP 提取公司信息
        return PDFParseResultDTO.CompanyInfo.builder()
                .companyName(extractCompanyName(text))
                .companyCode(extractCompanyCode(text))
                .build();
    }

    /**
     * 提取公司名称
     */
    private String extractCompanyName(String text) {
        // 简单的正则提取
        Pattern pattern = Pattern.compile("公司[全名称]{0,2}[：:](.*?)[\\n\\r]");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * 提取公司代码
     */
    private String extractCompanyCode(String text) {
        Pattern pattern = Pattern.compile("股票代码[：:](\\d{6})");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 提取报告期
     */
    private String extractReportPeriod(String text) {
        Pattern pattern = Pattern.compile("(\\d{4})年度?报告|报告期[：:].*(\\d{4})");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            String year = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            return year + "A";
        }
        return null;
    }

    /**
     * 从文本提取指标
     */
    private Map<String, Object> extractMetricsFromText(String text) {
        Map<String, Object> metrics = new HashMap<>();

        // TODO: 实现更复杂的指标提取逻辑
        // 示例：提取营业收入
        Pattern revenuePattern = Pattern.compile("营业收入.*?(\\d+[,，]?\\d*\\.?\\d*)");
        Matcher matcher = revenuePattern.matcher(text);
        if (matcher.find()) {
            String value = matcher.group(1).replace(",", "").replace("，", "");
            try {
                metrics.put("revenue", Double.parseDouble(value));
            } catch (NumberFormatException e) {
                log.warn("Failed to parse revenue value: {}", value);
            }
        }

        return metrics;
    }

    /**
     * 构建文本块
     */
    private List<PDFParseResultDTO.TextBlock> buildTextBlocks(PDDocument document) throws IOException {
        List<PDFParseResultDTO.TextBlock> textBlocks = new ArrayList<>();

        PDFTextStripper stripper = new PDFTextStripper();
        for (int i = 1; i <= document.getNumberOfPages(); i++) {
            stripper.setStartPage(i);
            stripper.setEndPage(i);
            String pageText = stripper.getText(document);

            textBlocks.add(PDFParseResultDTO.TextBlock.builder()
                    .pageNumber(i)
                    .text(pageText)
                    .build());
        }

        return textBlocks;
    }

}

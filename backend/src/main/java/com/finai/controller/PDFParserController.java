package com.finai.controller;

import com.finai.model.dto.PDFParseResultDTO;
import com.finai.service.SmartPDFParserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * PDF 智能解析控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
@Tag(name = "PDF解析", description = "PDF智能解析相关接口")
public class PDFParserController {

    private final SmartPDFParserService pdfParserService;

    @PostMapping("/parse")
    @Operation(summary = "解析PDF文件", description = "智能解析上传的PDF文件")
    public ResponseEntity<PDFParseResultDTO> parsePDF(
            @RequestParam("file") MultipartFile file) {
        log.info("Received PDF file for parsing: {}", file.getOriginalFilename());
        PDFParseResultDTO result = pdfParserService.parsePDF(file);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/parse/{filePath}")
    @Operation(summary = "解析PDF文件（路径）", description = "解析指定路径的PDF文件")
    public ResponseEntity<PDFParseResultDTO> parsePDFByPath(
            @PathVariable String filePath) {
        log.info("Parsing PDF from path: {}", filePath);
        PDFParseResultDTO result = pdfParserService.parsePDF(filePath);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/extract/tables")
    @Operation(summary = "提取表格", description = "从PDF中提取所有表格")
    public ResponseEntity<List<PDFParseResultDTO.TableData>> extractTables(
            @RequestParam String filePath) {
        log.info("Extracting tables from: {}", filePath);
        List<PDFParseResultDTO.TableData> tables = pdfParserService.extractTables(filePath);
        return ResponseEntity.ok(tables);
    }

    @GetMapping("/extract/charts")
    @Operation(summary = "提取图表", description = "从PDF中提取所有图表")
    public ResponseEntity<List<PDFParseResultDTO.ChartData>> extractCharts(
            @RequestParam String filePath) {
        log.info("Extracting charts from: {}", filePath);
        List<PDFParseResultDTO.ChartData> charts = pdfParserService.extractCharts(filePath);
        return ResponseEntity.ok(charts);
    }

    @GetMapping("/extract/metrics")
    @Operation(summary = "提取财务指标", description = "从PDF中提取财务指标")
    public ResponseEntity<Map<String, Object>> extractMetrics(
            @RequestParam String filePath) {
        log.info("Extracting financial metrics from: {}", filePath);
        Map<String, Object> metrics = pdfParserService.extractFinancialMetrics(filePath);
        return ResponseEntity.ok(metrics);
    }

    @PostMapping("/align/multi-year")
    @Operation(summary = "多年报表对齐", description = "对齐多个年度的财务报表")
    public ResponseEntity<PDFParseResultDTO.AlignedData> alignMultiYearReports(
            @RequestBody List<String> filePaths) {
        log.info("Aligning multi-year reports: {}", filePaths);
        PDFParseResultDTO.AlignedData alignedData = pdfParserService.alignMultiYearReports(filePaths);
        return ResponseEntity.ok(alignedData);
    }

    @GetMapping("/identify/type")
    @Operation(summary = "识别报表类型", description = "智能识别PDF报表的类型")
    public ResponseEntity<PDFParseResultDTO.ReportType> identifyReportType(
            @RequestParam String filePath) {
        log.info("Identifying report type for: {}", filePath);
        PDFParseResultDTO.ReportType reportType = pdfParserService.identifyReportType(filePath);
        return ResponseEntity.ok(reportType);
    }

    @GetMapping("/extract/notes")
    @Operation(summary = "提取附注", description = "提取报表附注信息")
    public ResponseEntity<List<PDFParseResultDTO.NoteInfo>> extractNotes(
            @RequestParam String filePath) {
        log.info("Extracting notes from: {}", filePath);
        List<PDFParseResultDTO.NoteInfo> notes = pdfParserService.extractNotes(filePath);
        return ResponseEntity.ok(notes);
    }
}

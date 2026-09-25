package com.finai.service;

import com.finai.model.dto.PDFParseResultDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * PDF 智能解析服务
 * 增强的 PDF 解析能力，支持表格识别、图表提取、多年报表对齐
 */
public interface SmartPDFParserService {

    /**
     * 智能解析 PDF 文件
     * @param file PDF 文件
     * @return 解析结果
     */
    PDFParseResultDTO parsePDF(MultipartFile file);

    /**
     * 解析 PDF 文件（从路径）
     * @param filePath 文件路径
     * @return 解析结果
     */
    PDFParseResultDTO parsePDF(String filePath);

    /**
     * 提取表格
     * @param filePath PDF 文件路径
     * @return 表格列表
     */
    List<PDFParseResultDTO.TableData> extractTables(String filePath);

    /**
     * 提取图表
     * @param filePath PDF 文件路径
     * @return 图表列表
     */
    List<PDFParseResultDTO.ChartData> extractCharts(String filePath);

    /**
     * 提取财务指标
     * @param filePath PDF 文件路径
     * @return 财务指标映射
     */
    Map<String, Object> extractFinancialMetrics(String filePath);

    /**
     * 多年报表对齐
     * @param filePaths 多个年度的报表文件路径
     * @return 对齐后的数据
     */
    PDFParseResultDTO.AlignedData alignMultiYearReports(List<String> filePaths);

    /**
     * OCR 识别图片中的表格
     * @param imagePath 图片路径
     * @return 识别出的表格数据
     */
    PDFParseResultDTO.TableData ocrTable(String imagePath);

    /**
     * 智能识别报表类型
     * @param filePath PDF 文件路径
     * @return 报表类型
     */
    PDFParseResultDTO.ReportType identifyReportType(String filePath);

    /**
     * 提取附注信息
     * @param filePath PDF 文件路径
     * @return 附注信息列表
     */
    List<PDFParseResultDTO.NoteInfo> extractNotes(String filePath);
}

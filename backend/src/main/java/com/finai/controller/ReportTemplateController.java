package com.finai.controller;

import com.finai.model.dto.ReportTemplateDTO;
import com.finai.service.ReportTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;

/**
 * 报告模板定制控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "报告模板", description = "报告模板定制相关接口")
public class ReportTemplateController {

    private final ReportTemplateService reportTemplateService;

    @PostMapping("/{taskId}/generate")
    @Operation(summary = "生成报告", description = "根据模板生成报告")
    public ResponseEntity<ReportTemplateDTO> generateReport(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "TPL_STANDARD") String templateId) {
        log.info("Generating report for task {} with template {}", taskId, templateId);
        ReportTemplateDTO report = reportTemplateService.generateReport(taskId, templateId);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/{taskId}/export/markdown")
    @Operation(summary = "导出Markdown", description = "导出Markdown格式报告")
    public ResponseEntity<String> exportToMarkdown(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "TPL_STANDARD") String templateId) {
        log.info("Exporting to Markdown for task {}", taskId);
        String markdown = reportTemplateService.exportToMarkdown(taskId, templateId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_MARKDOWN)
                .body(markdown);
    }

    @GetMapping("/{taskId}/export/word")
    @Operation(summary = "导出Word", description = "导出Word格式报告")
    public ResponseEntity<Resource> exportToWord(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "TPL_STANDARD") String templateId) {
        log.info("Exporting to Word for task {}", taskId);
        String filePath = reportTemplateService.exportToWord(taskId, templateId);

        File file = new File(filePath);
        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.docx\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/{taskId}/export/pdf")
    @Operation(summary = "导出PDF", description = "导出PDF格式报告")
    public ResponseEntity<Resource> exportToPDF(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "TPL_STANDARD") String templateId) {
        log.info("Exporting to PDF for task {}", taskId);
        String filePath = reportTemplateService.exportToPDF(taskId, templateId);

        File file = new File(filePath);
        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }

    @GetMapping("/{taskId}/preview")
    @Operation(summary = "预览报告", description = "预览报告（HTML格式）")
    public ResponseEntity<String> previewReport(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "TPL_STANDARD") String templateId) {
        log.info("Previewing report for task {}", taskId);
        String html = reportTemplateService.previewReport(taskId, templateId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    @PostMapping("/templates")
    @Operation(summary = "创建模板", description = "创建自定义报告模板")
    public ResponseEntity<ReportTemplateDTO> createTemplate(
            @RequestBody ReportTemplateDTO template) {
        log.info("Creating new template: {}", template.getTemplateName());
        ReportTemplateDTO created = reportTemplateService.createTemplate(template);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/templates/{templateId}")
    @Operation(summary = "更新模板", description = "更新报告模板")
    public ResponseEntity<ReportTemplateDTO> updateTemplate(
            @PathVariable String templateId,
            @RequestBody ReportTemplateDTO template) {
        log.info("Updating template: {}", templateId);
        ReportTemplateDTO updated = reportTemplateService.updateTemplate(templateId, template);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/templates/{templateId}")
    @Operation(summary = "删除模板", description = "删除报告模板")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String templateId) {
        log.info("Deleting template: {}", templateId);
        reportTemplateService.deleteTemplate(templateId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/templates")
    @Operation(summary = "获取所有模板", description = "获取所有可用的报告模板")
    public ResponseEntity<List<ReportTemplateDTO>> listTemplates() {
        log.info("Listing all templates");
        List<ReportTemplateDTO> templates = reportTemplateService.listTemplates();
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/templates/{templateId}")
    @Operation(summary = "获取模板详情", description = "获取指定模板的详细信息")
    public ResponseEntity<ReportTemplateDTO> getTemplate(@PathVariable String templateId) {
        log.info("Getting template: {}", templateId);
        ReportTemplateDTO template = reportTemplateService.getTemplate(templateId);
        return ResponseEntity.ok(template);
    }
}

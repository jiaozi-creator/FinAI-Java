package com.finai.service.impl;

import com.finai.model.AnalysisTask;
import com.finai.model.dto.ReportTemplateDTO;
import com.finai.repository.AnalysisTaskRepository;
import com.finai.service.ReportTemplateService;
import com.finai.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ReportTemplateServiceImpl implements ReportTemplateService {

    private final AnalysisTaskRepository taskRepository;
    private final Map<String, ReportTemplateDTO> templates = new ConcurrentHashMap<>();

    public ReportTemplateServiceImpl(AnalysisTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
        initializeDefaultTemplates();
    }

    @Override
    public ReportTemplateDTO generateReport(String taskId, String templateId) {
        log.info("Generating report for task {} with template {}", taskId, templateId);

        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        ReportTemplateDTO template = templates.get(templateId);
        if (template == null) {
            throw new ResourceNotFoundException("Template not found: " + templateId);
        }

        String renderedContent = renderTemplate(task, template);
        template.setUsageCount(template.getUsageCount() + 1);

        return ReportTemplateDTO.builder()
                .templateId(templateId)
                .templateName(template.getTemplateName())
                .templateContent(renderedContent)
                .build();
    }

    @Override
    public String exportToMarkdown(String taskId, String templateId) {
        log.info("Exporting to Markdown for task {}", taskId);
        ReportTemplateDTO report = generateReport(taskId, templateId);
        return report.getTemplateContent();
    }

    @Override
    public String exportToWord(String taskId, String templateId) {
        log.info("Exporting to Word for task {}", taskId);
        String markdown = exportToMarkdown(taskId, templateId);
        String outputDir = "./outputs/reports";
        try {
            Files.createDirectories(Paths.get(outputDir));
            String filePath = outputDir + "/" + taskId + "_report.docx";
            log.warn("Word export not fully implemented, saving as text file");
            Files.writeString(Paths.get(filePath.replace(".docx", ".txt")), markdown);
            return filePath;
        } catch (IOException e) {
            log.error("Failed to export to Word", e);
            throw new RuntimeException("Export failed: " + e.getMessage());
        }
    }

    @Override
    public String exportToPDF(String taskId, String templateId) {
        log.info("Exporting to PDF for task {}", taskId);
        String markdown = exportToMarkdown(taskId, templateId);
        String outputDir = "./outputs/reports";
        try {
            Files.createDirectories(Paths.get(outputDir));
            String filePath = outputDir + "/" + taskId + "_report.pdf";
            log.warn("PDF export not fully implemented, saving as text file");
            Files.writeString(Paths.get(filePath.replace(".pdf", ".txt")), markdown);
            return filePath;
        } catch (IOException e) {
            log.error("Failed to export to PDF", e);
            throw new RuntimeException("Export failed: " + e.getMessage());
        }
    }

    @Override
    public ReportTemplateDTO createTemplate(ReportTemplateDTO template) {
        log.info("Creating new template: {}", template.getTemplateName());
        String templateId = "TPL_" + UUID.randomUUID().toString().substring(0, 12);
        template.setTemplateId(templateId);
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        template.setUsageCount(0);
        template.setEnabled(true);
        templates.put(templateId, template);
        return template;
    }

    @Override
    public ReportTemplateDTO updateTemplate(String templateId, ReportTemplateDTO template) {
        log.info("Updating template: {}", templateId);
        ReportTemplateDTO existing = templates.get(templateId);
        if (existing == null) {
            throw new ResourceNotFoundException("Template not found: " + templateId);
        }
        template.setTemplateId(templateId);
        template.setCreatedAt(existing.getCreatedAt());
        template.setUpdatedAt(LocalDateTime.now());
        template.setUsageCount(existing.getUsageCount());
        templates.put(templateId, template);
        return template;
    }

    @Override
    public void deleteTemplate(String templateId) {
        log.info("Deleting template: {}", templateId);
        ReportTemplateDTO template = templates.get(templateId);
        if (template == null) {
            throw new ResourceNotFoundException("Template not found: " + templateId);
        }
        if (template.getIsDefault()) {
            throw new IllegalStateException("Cannot delete default template");
        }
        templates.remove(templateId);
    }

    @Override
    public List<ReportTemplateDTO> listTemplates() {
        return new ArrayList<>(templates.values());
    }

    @Override
    public ReportTemplateDTO getTemplate(String templateId) {
        ReportTemplateDTO template = templates.get(templateId);
        if (template == null) {
            throw new ResourceNotFoundException("Template not found: " + templateId);
        }
        return template;
    }

    @Override
    public String previewReport(String taskId, String templateId) {
        log.info("Previewing report for task {} with template {}", taskId, templateId);
        String markdown = exportToMarkdown(taskId, templateId);
        return convertMarkdownToHTML(markdown);
    }

    private void initializeDefaultTemplates() {
        ReportTemplateDTO standardTemplate = ReportTemplateDTO.builder()
                .templateId("TPL_STANDARD")
                .templateName("Standard Report")
                .description("Complete report with all analysis dimensions")
                .templateType(ReportTemplateDTO.TemplateType.SYSTEM)
                .isDefault(true)
                .enabled(true)
                .createdBy("system")
                .createdAt(LocalDateTime.now())
                .usageCount(0)
                .tags(List.of("complete", "standard", "recommended"))
                .build();
        templates.put("TPL_STANDARD", standardTemplate);

        ReportTemplateDTO simpleTemplate = ReportTemplateDTO.builder()
                .templateId("TPL_SIMPLE")
                .templateName("Simple Report")
                .description("Core metrics and risk assessment only")
                .templateType(ReportTemplateDTO.TemplateType.SYSTEM)
                .isDefault(false)
                .enabled(true)
                .createdBy("system")
                .createdAt(LocalDateTime.now())
                .usageCount(0)
                .tags(List.of("simplified", "quick"))
                .build();
        templates.put("TPL_SIMPLE", simpleTemplate);

        ReportTemplateDTO investmentTemplate = ReportTemplateDTO.builder()
                .templateId("TPL_INVESTMENT")
                .templateName("Investment Report")
                .description("Focus on valuation and investment recommendations")
                .templateType(ReportTemplateDTO.TemplateType.SYSTEM)
                .isDefault(false)
                .enabled(true)
                .createdBy("system")
                .createdAt(LocalDateTime.now())
                .usageCount(0)
                .tags(List.of("investment", "valuation", "recommendations"))
                .build();
        templates.put("TPL_INVESTMENT", investmentTemplate);
    }

    private String renderTemplate(AnalysisTask task, ReportTemplateDTO template) {
        StringBuilder report = new StringBuilder();
        report.append("# Financial Analysis Report: ").append(task.getCompanyName()).append("\n\n");
        report.append("**Report Period**: ").append(task.getReportPeriod()).append("\n\n");
        report.append("**Generated At**: ").append(LocalDateTime.now()).append("\n\n");
        report.append("**Analysis Type**: ").append(task.getAnalysisType()).append("\n\n");
        report.append("---\n\n");

        switch (template.getTemplateId()) {
            case "TPL_STANDARD":
                report.append(generateStandardReport(task));
                break;
            case "TPL_SIMPLE":
                report.append(generateSimpleReport(task));
                break;
            case "TPL_INVESTMENT":
                report.append(generateInvestmentReport(task));
                break;
            default:
                report.append(generateStandardReport(task));
        }

        report.append("\n\n---\n\n");
        report.append("*This report is automatically generated by FinAI for reference only.*\n");
        return report.toString();
    }

    private String generateStandardReport(AnalysisTask task) {
        return String.format("""
                ## 1. Company Overview

                - Company Code: %s
                - Company Name: %s
                - Report Period: %s

                ## 2. Financial Metrics

                ### Profitability
                - ROE: To be supplemented
                - Gross Margin: To be supplemented
                - Net Margin: To be supplemented

                ### Solvency
                - Current Ratio: To be supplemented
                - Quick Ratio: To be supplemented
                - Debt Ratio: To be supplemented

                ## 3. Risk Assessment

                To be supplemented

                ## 4. Industry Comparison

                To be supplemented

                ## 5. Forecast

                To be supplemented

                ## 6. Investment Recommendations

                To be supplemented
                """, task.getCompanyCode(), task.getCompanyName(), task.getReportPeriod());
    }

    private String generateSimpleReport(AnalysisTask task) {
        return """
                ## Summary

                ### Key Metrics
                - ROE: To be supplemented
                - Gross Margin: To be supplemented
                - Debt Ratio: To be supplemented

                ### Risk Assessment
                To be supplemented

                ### Conclusion
                To be supplemented
                """;
    }

    private String generateInvestmentReport(AnalysisTask task) {
        return """
                ## Investment Analysis

                ### Valuation
                - DCF: To be supplemented
                - Relative Valuation: To be supplemented

                ### Investment Highlights
                To be supplemented

                ### Risk Warnings
                To be supplemented

                ### Recommendation
                To be supplemented
                """;
    }

    private String convertMarkdownToHTML(String markdown) {
        String html = markdown
                .replaceAll("# (.*)", "<h1>$1</h1>")
                .replaceAll("## (.*)", "<h2>$1</h2>")
                .replaceAll("### (.*)", "<h3>$1</h3>")
                .replaceAll("\\*\\*(.*?)\\*\\*", "<strong>$1</strong>")
                .replaceAll("\\n\\n", "</p><p>")
                .replaceAll("^", "<p>")
                .replaceAll("$", "</p>");

        return String.format("""
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Report Preview</title>
                    <style>
                        body { font-family: Arial, sans-serif; margin: 40px; }
                        h1 { color: #333; }
                        h2 { color: #666; border-bottom: 1px solid #ddd; padding-bottom: 10px; }
                        h3 { color: #888; }
                    </style>
                </head>
                <body>
                %s
                </body>
                </html>
                """, html);
    }
}

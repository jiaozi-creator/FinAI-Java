package com.finai.service;

import com.finai.model.dto.ReportTemplateDTO;

import java.util.List;
import java.util.Map;

/**
 * 报告模板定制服务
 * 支持自定义报告模板和多格式导出
 */
public interface ReportTemplateService {

    /**
     * 根据模板生成报告
     * @param taskId 任务ID
     * @param templateId 模板ID
     * @return 生成的报告
     */
    ReportTemplateDTO generateReport(String taskId, String templateId);

    /**
     * 导出报告（Markdown格式）
     * @param taskId 任务ID
     * @param templateId 模板ID
     * @return Markdown内容
     */
    String exportToMarkdown(String taskId, String templateId);

    /**
     * 导出报告（Word格式）
     * @param taskId 任务ID
     * @param templateId 模板ID
     * @return Word文件路径
     */
    String exportToWord(String taskId, String templateId);

    /**
     * 导出报告（PDF格式）
     * @param taskId 任务ID
     * @param templateId 模板ID
     * @return PDF文件路径
     */
    String exportToPDF(String taskId, String templateId);

    /**
     * 创建自定义模板
     * @param template 模板定义
     * @return 创建的模板
     */
    ReportTemplateDTO createTemplate(ReportTemplateDTO template);

    /**
     * 更新模板
     * @param templateId 模板ID
     * @param template 模板定义
     * @return 更新后的模板
     */
    ReportTemplateDTO updateTemplate(String templateId, ReportTemplateDTO template);

    /**
     * 删除模板
     * @param templateId 模板ID
     */
    void deleteTemplate(String templateId);

    /**
     * 获取所有模板
     * @return 模板列表
     */
    List<ReportTemplateDTO> listTemplates();

    /**
     * 获取模板详情
     * @param templateId 模板ID
     * @return 模板详情
     */
    ReportTemplateDTO getTemplate(String templateId);

    /**
     * 预览报告
     * @param taskId 任务ID
     * @param templateId 模板ID
     * @return 预览内容（HTML）
     */
    String previewReport(String taskId, String templateId);
}

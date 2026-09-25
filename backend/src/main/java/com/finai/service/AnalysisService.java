package com.finai.service;

import com.finai.model.AnalysisTask;
import com.finai.model.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 分析服务接口
 */
public interface AnalysisService {

    /**
     * 创建分析任务
     *
     * @param request 任务请求
     * @param file 上传的文件(可选)
     * @return 任务响应
     */
    TaskResponseDTO createTask(TaskRequestDTO request, MultipartFile file);

    /**
     * 获取任务详情
     *
     * @param taskId 任务ID
     * @return 任务响应
     */
    TaskResponseDTO getTask(String taskId);

    /**
     * 获取任务列表
     *
     * @param companyCode 公司代码(可选)
     * @param status 任务状态(可选)
     * @param page 页码
     * @param size 每页大小
     * @return 任务列表
     */
    List<TaskResponseDTO> listTasks(String companyCode, AnalysisTask.TaskStatus status, int page, int size);

    /**
     * 获取任务报告
     *
     * @param taskId 任务ID
     * @return 分析报告
     */
    AnalysisReportDTO getReport(String taskId);

    /**
     * 获取财务指标
     *
     * @param taskId 任务ID
     * @return 财务指标
     */
    FinancialMetricsDTO getMetrics(String taskId);

    /**
     * 获取异常信号
     *
     * @param taskId 任务ID
     * @return 异常信号列表
     */
    List<AnomalySignalDTO> getAnomalies(String taskId);

    /**
     * 获取估值结果
     *
     * @param taskId 任务ID
     * @return 估值结果
     */
    ValuationResultDTO getValuation(String taskId);

    /**
     * 获取证据列表
     *
     * @param taskId 任务ID
     * @param fieldId 字段ID(可选)
     * @return 证据列表
     */
    List<EvidenceDTO> getEvidence(String taskId, String fieldId);

    /**
     * 取消任务
     *
     * @param taskId 任务ID
     */
    void cancelTask(String taskId);

    /**
     * 删除任务
     *
     * @param taskId 任务ID
     */
    void deleteTask(String taskId);

    /**
     * 执行分析任务
     *
     * @param taskId 任务ID
     */
    void executeTask(String taskId);
}

package com.finai.service;

import com.finai.model.dto.ChatMessageDTO;
import com.finai.model.dto.ChatResponseDTO;

import java.util.List;

/**
 * 智能对话分析服务
 * 支持用户用自然语言询问财务分析问题
 */
public interface ChatAnalysisService {

    /**
     * 提问关于任务的问题
     * @param taskId 任务ID
     * @param question 用户问题
     * @return 智能回答
     */
    ChatResponseDTO askQuestion(String taskId, String question);

    /**
     * 获取对话历史
     * @param taskId 任务ID
     * @return 对话历史列表
     */
    List<ChatMessageDTO> getChatHistory(String taskId);

    /**
     * 清除对话历史
     * @param taskId 任务ID
     */
    void clearChatHistory(String taskId);

    /**
     * 提供智能建议问题
     * @param taskId 任务ID
     * @return 建议的问题列表
     */
    List<String> getSuggestedQuestions(String taskId);
}

package com.finai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 聊天消息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 角色：user 或 assistant
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 引用的数据源（如具体的财务指标、证据ID等）
     */
    private String[] references;
}

package com.finai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 聊天响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDTO {

    /**
     * 回答内容
     */
    private String answer;

    /**
     * 引用的证据
     */
    private List<EvidenceReference> evidences;

    /**
     * 相关的财务指标
     */
    private List<MetricReference> metrics;

    /**
     * 置信度 (0-1)
     */
    private Double confidence;

    /**
     * 建议的后续问题
     */
    private List<String> suggestedQuestions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceReference {
        private String evidenceId;
        private String fieldId;
        private String excerpt;
        private String sourceFile;
        private Integer pageNumber;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricReference {
        private String metricName;
        private Object value;
        private String period;
        private String unit;
    }
}

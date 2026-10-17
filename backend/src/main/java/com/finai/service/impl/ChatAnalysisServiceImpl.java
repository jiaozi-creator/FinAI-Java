package com.finai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finai.analysis.ChatAnswerFormatter;
import com.finai.config.LlmRuntime;
import com.finai.exception.ResourceNotFoundException;
import com.finai.model.AnalysisSnapshot;
import com.finai.model.AnalysisTask;
import com.finai.model.Evidence;
import com.finai.model.dto.ChatMessageDTO;
import com.finai.model.dto.ChatResponseDTO;
import com.finai.repository.AnalysisSnapshotRepository;
import com.finai.repository.AnalysisTaskRepository;
import com.finai.repository.EvidenceRepository;
import com.finai.service.AuditLogService;
import com.finai.service.ChatAnalysisService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能对话分析服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAnalysisServiceImpl implements ChatAnalysisService {

    private final AnalysisTaskRepository taskRepository;
    private final AnalysisSnapshotRepository snapshotRepository;
    private final EvidenceRepository evidenceRepository;
    private final ChatLanguageModel chatModel;
    private final LlmRuntime llmRuntime;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    // 存储每个任务的对话历史 (内存存储，生产环境应使用Redis)
    private final Map<String, List<ChatMessageDTO>> conversationHistory = new ConcurrentHashMap<>();
    private final Map<String, Map<String, ChatResponseDTO>> requestResults = new ConcurrentHashMap<>();
    private final Map<String, String> activeRunIds = new ConcurrentHashMap<>();

    @Override
    public ChatResponseDTO askQuestion(String taskId, String question) {
        return askQuestion(taskId, question, UUID.randomUUID().toString());
    }

    @Override
    public ChatResponseDTO askQuestion(String taskId, String question, String requestId) {
        if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
        ChatResponseDTO existing = requestResults.getOrDefault(taskId, Map.of()).get(requestId);
        if (existing != null) return existing;
        log.info("Processing question for task {}: {}", taskId, question);

        // 验证任务存在
        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        // 构建上下文
        AnalysisSnapshot snapshot = task.getStatus() == AnalysisTask.TaskStatus.COMPLETED ? snapshotRepository.findByTaskId(taskId).orElse(null) : null;
        String runId = snapshot == null ? null : snapshotRunId(snapshot);
        if (runId != null && !Objects.equals(runId, activeRunIds.put(taskId, runId))) {
            conversationHistory.remove(taskId);
        }
        Map<String, ChatAnswerFormatter.Fact> facts = snapshot == null ? Map.of() : parseFacts(snapshot.getMetricsJson(), question, task.getReportPeriod());
        String context = buildContext(task, question, snapshot, facts);

        // 获取历史对话
        List<ChatMessageDTO> history = conversationHistory.getOrDefault(taskId, List.of());

        // 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();

        // 系统消息
        messages.add(SystemMessage.from(buildSystemPrompt(task, context)));

        // 历史对话
        for (ChatMessageDTO msg : history.stream().filter(msg -> "COMPUTED".equals(msg.getStatus())).skip(Math.max(0, history.stream().filter(msg -> "COMPUTED".equals(msg.getStatus())).count() - 12)).toList()) {
            if ("user".equals(msg.getRole())) {
                messages.add(UserMessage.from(msg.getContent()));
            } else {
                messages.add(AiMessage.from(msg.getContent()));
            }
        }

        // 当前问题
        messages.add(UserMessage.from(question));

        String answer;
        String responseStatus = "COMPUTED";
        String degradationReason = null;
        long started = System.currentTimeMillis();
        String prompt = transcript(messages);
        try {
            String raw = chatModel.generate(messages).content().text();
            ChatAnswerFormatter.Output output;
            try {
                output = ChatAnswerFormatter.render(raw, facts, isDetailed(question), objectMapper);
            } catch (RuntimeException invalid) {
                messages.add(AiMessage.from(raw == null ? "" : raw));
                messages.add(UserMessage.from("修正一次：只输出 {\"claims\":[{\"field\":\"允许字段标识\",\"text\":\"不含数字的解释\"}]}；不得输出快照、JSON以外内容或自造数字。"));
                raw = chatModel.generate(messages).content().text();
                try { output = ChatAnswerFormatter.render(raw, facts, isDetailed(question), objectMapper); }
                catch (RuntimeException stillInvalid) {
                    output = ChatAnswerFormatter.fallback(facts);
                    responseStatus = "DEGRADED";
                    degradationReason = stillInvalid.getMessage() == null ? "FORMAT_OR_NUMBER_REJECTED" : stillInvalid.getMessage();
                }
            }
            answer = output.text();
            auditLogService.logLLMRequest(taskId, chatDetails() + ",validation=" + responseStatus, llmRuntime.getModel(), prompt, raw + "\n\n[DISPLAY]\n" + answer,
                    System.currentTimeMillis() - started);
        } catch (Exception e) {
            log.error("Failed to generate answer", e);
            auditLogService.logError(taskId, "LLM_CALL", e.getMessage());
            auditLogService.logError(taskId, "CHAT_DEGRADED", e.getClass().getSimpleName());
            outputError(taskId, e);
            answer = ChatAnswerFormatter.fallback(facts).text();
            responseStatus = "DEGRADED";
            degradationReason = e.getClass().getSimpleName();
        }

        saveConversation(taskId, question, answer, requestId, responseStatus);

        ChatResponseDTO response = ChatResponseDTO.builder()
                .answer(answer)
                .evidences(evidenceRefs(taskId))
                .confidence(null)
                .suggestedQuestions(generateSuggestedQuestions(task))
                .requestId(requestId).status(responseStatus).degradationReason(degradationReason).build();
        requestResults.computeIfAbsent(taskId, k -> new ConcurrentHashMap<>()).put(requestId, response);
        return response;
    }

    @Override
    public List<ChatMessageDTO> getChatHistory(String taskId) {
        List<ChatMessageDTO> history = conversationHistory.getOrDefault(taskId, List.of());
        synchronized (history) { return List.copyOf(history); }
    }

    @Override
    public void clearChatHistory(String taskId) {
        conversationHistory.remove(taskId);
        requestResults.remove(taskId);
        log.info("Cleared chat history for task: {}", taskId);
    }

    @Override
    public List<String> getSuggestedQuestions(String taskId) {
        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        return generateSuggestedQuestions(task);
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(AnalysisTask task, String context) {
        return String.format("""
                你是财务分析助手，解答 %s (%s) 在 %s 的问题。

                任务状态：%s
                分析类型：%s

                可用材料：
                %s

                只允许引用材料中的字段标识。严格输出内部JSON：{"claims":[{"field":"字段标识","text":"简短、无数字的解释","unit":"原单位或亿元/万元/%","period":"期间","scope":"范围"}]}。
                不得填写 value，不得带金额、百分比、年份外数字或JSON之外正文。根据问题最多引用4项；明确要求详细分析时最多12项。
                不要给买卖建议。对话历史只在内存里，审计日志会留下本次提问和回复。
                """,
                task.getCompanyName(),
                task.getCompanyCode(),
                task.getReportPeriod(),
                task.getStatus(),
                task.getAnalysisType(),
                context
        );
    }

    private String chatDetails() {
        return "provider=" + llmRuntime.getProvider()
                + ",model=" + llmRuntime.getModel()
                + ",temperature=" + llmRuntime.getTemperature()
                + ",leavesMachine=" + llmRuntime.leavesMachine()
                + ",payload=skill+snapshot,rawFile=false,history=memory";
    }

    private static String transcript(List<ChatMessage> messages) {
        StringBuilder text = new StringBuilder();
        for (ChatMessage message : messages) {
            text.append(message.type()).append('\n').append(message.text()).append("\n---\n");
        }
        return text.toString();
    }

    private static String readSkill() {
        try {
            return new ClassPathResource("skills/financial-statement-analysis.md").getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 构建任务上下文
     */
    private String buildContext(AnalysisTask task, String question, AnalysisSnapshot snapshot, Map<String, ChatAnswerFormatter.Fact> facts) {
        StringBuilder context = new StringBuilder();

        // 基本信息
        context.append("## 基本信息\n");
        context.append(String.format("- 公司：%s (%s)\n", task.getCompanyName(), task.getCompanyCode()));
        context.append(String.format("- 报告期：%s\n", task.getReportPeriod()));
        context.append(String.format("- 创建时间：%s\n", task.getCreatedAt()));

        // 如果任务完成，添加更多详细信息
        if (task.getStatus() != AnalysisTask.TaskStatus.COMPLETED) {
            context.append(String.format("\n## 当前进度\n%d%% - %s\n",
                task.getProgress(), task.getCurrentStep()));
            return context.toString();
        }
        if (snapshot == null) {
            context.append("\n分析结果不存在。所有数字回答未提取。\n");
            return context.toString();
        }
        context.append("\n## 当前运行的相关指标\n");
        context.append(ChatAnswerFormatter.material(facts));
        context.append("\n## 异常\n");
        context.append(snapshot.getAnomaliesJson() == null ? "未提取\n" : snapshot.getAnomaliesJson());
        context.append("\n## 估值\n");
        context.append(snapshot.getValuationJson() == null ? "未提取\n" : snapshot.getValuationJson());
        context.append("\n## 证据\n");
        List<Evidence> evidence = evidenceRepository.findByTaskIdOrderByFieldId(task.getTaskId());
        int shown = 0;
        for (Evidence row : evidence) {
            if (!facts.containsKey(row.getFieldId())) continue;
            if (row.getCellRef() != null && row.getCellRef().startsWith("prior")) {
                continue;
            }
            context.append("- ").append(row.getFieldName())
                    .append(" 第").append(row.getPage() == null ? "-" : row.getPage()).append("页 ")
                    .append(row.getValue()).append(" ").append(row.getUnit() == null ? "" : row.getUnit())
                    .append(" ").append(relevantEvidenceExcerpt(row))
                    .append("\n");
            if (++shown >= 6) {
                break;
            }
        }
        return context.toString();
    }

    private Map<String, ChatAnswerFormatter.Fact> parseFacts(String json, String question, String period) {
        try { var metrics = objectMapper.readValue(json, com.finai.model.dto.FinancialMetricsDTO.class); return ChatAnswerFormatter.select(metrics, question); }
        catch (Exception ex) { auditLogService.logError("chat", "SNAPSHOT_PARSE", ex.getClass().getSimpleName()); return Map.of(); }
    }

    private String snapshotRunId(AnalysisSnapshot snapshot) {
        try { return objectMapper.readTree(snapshot.getReportJson()).path("executionSummary").path("runId").asText(null); }
        catch (Exception e) { return null; }
    }

    private boolean isDetailed(String question) { return question != null && question.matches("(?s).*(详细|展开|逐项|完整分析).*" ); }

    private void outputError(String taskId, Exception error) { auditLogService.logError(taskId, "CHAT_DEGRADED", error.getClass().getSimpleName()); }

    private List<ChatResponseDTO.EvidenceReference> evidenceRefs(String taskId) {
        return evidenceRepository.findByTaskIdOrderByFieldId(taskId).stream()
                .filter(row -> row.getCellRef() == null || !row.getCellRef().startsWith("prior"))
                .limit(12)
                .map(row -> ChatResponseDTO.EvidenceReference.builder()
                        .evidenceId(row.getId() == null ? null : String.valueOf(row.getId()))
                        .fieldId(row.getFieldId())
                        .excerpt(row.getFieldName() + " " + row.getValue())
                        .sourceFile(row.getSourceFile())
                        .pageNumber(row.getPage())
                        .build())
                .toList();
    }

    /**
     * 保存对话
     */
    private void saveConversation(String taskId, String question, String answer, String requestId, String status) {
        List<ChatMessageDTO> history = conversationHistory.computeIfAbsent(taskId, k -> Collections.synchronizedList(new ArrayList<>()));

        // 保存用户消息
        history.add(ChatMessageDTO.builder()
                .messageId(UUID.randomUUID().toString())
                .taskId(taskId)
                .role("user")
                .content(question)
                .createdAt(LocalDateTime.now())
                .requestId(requestId).status(status)
                .build());

        // 保存AI回复
        history.add(ChatMessageDTO.builder()
                .messageId(UUID.randomUUID().toString())
                .taskId(taskId)
                .role("assistant")
                .content(answer)
                .createdAt(LocalDateTime.now())
                .requestId(requestId).status(status)
                .build());
    }

    private String relevantEvidenceExcerpt(Evidence evidence) {
        if (evidence.getSnippet() == null) return "";
        return Arrays.stream(evidence.getSnippet().split("\\R")).filter(line -> evidence.getFieldName() != null && line.contains(evidence.getFieldName()))
                .limit(3).collect(java.util.stream.Collectors.joining(" "));
    }

    /**
     * 生成建议问题
     */
    private List<String> generateSuggestedQuestions(AnalysisTask task) {
        List<String> questions = new ArrayList<>();

        questions.add("这家公司的盈利能力如何？");
        questions.add("现金流状况是否健康？");
        questions.add("有哪些财务风险需要关注？");

        if (task.getStatus() == AnalysisTask.TaskStatus.COMPLETED) {
            questions.add("检测到了哪些异常信号？");

            if (task.getAnalysisType() == AnalysisTask.AnalysisType.FULL ||
                task.getAnalysisType() == AnalysisTask.AnalysisType.VALUATION_ONLY) {
                questions.add("DCF 用了哪些假设？估值区间怎么来的？");
            }
        }

        return questions;
    }
}

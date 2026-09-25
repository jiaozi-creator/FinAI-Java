package com.finai.service.impl;

import com.finai.model.AnalysisSnapshot;
import com.finai.model.AnalysisTask;
import com.finai.model.Evidence;
import com.finai.model.dto.ChatMessageDTO;
import com.finai.model.dto.ChatResponseDTO;
import com.finai.repository.AnalysisSnapshotRepository;
import com.finai.repository.AnalysisTaskRepository;
import com.finai.repository.EvidenceRepository;
import com.finai.service.ChatAnalysisService;
import com.finai.exception.ResourceNotFoundException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能对话分析服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAnalysisServiceImpl implements ChatAnalysisService {

    private static final Pattern NUMBER = Pattern.compile("-?\\d[\\d,，]*(?:\\.\\d+)?");

    private final AnalysisTaskRepository taskRepository;
    private final AnalysisSnapshotRepository snapshotRepository;
    private final EvidenceRepository evidenceRepository;
    private final ChatLanguageModel chatModel;

    // 存储每个任务的对话历史 (内存存储，生产环境应使用Redis)
    private final Map<String, List<ChatMessageDTO>> conversationHistory = new ConcurrentHashMap<>();

    @Override
    public ChatResponseDTO askQuestion(String taskId, String question) {
        log.info("Processing question for task {}: {}", taskId, question);

        // 验证任务存在
        AnalysisTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        // 构建上下文
        String context = buildContext(task);

        // 获取历史对话
        List<ChatMessageDTO> history = conversationHistory.getOrDefault(taskId, new ArrayList<>());

        // 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();

        // 系统消息
        messages.add(SystemMessage.from(buildSystemPrompt(task, context)));

        // 历史对话
        for (ChatMessageDTO msg : history) {
            if ("user".equals(msg.getRole())) {
                messages.add(UserMessage.from(msg.getContent()));
            } else {
                messages.add(AiMessage.from(msg.getContent()));
            }
        }

        // 当前问题
        messages.add(UserMessage.from(question));

        // 调用LLM
        String answer;
        try {
            answer = chatModel.generate(messages).content().text();
            answer = guardNumbers(answer, context);
        } catch (Exception e) {
            log.error("Failed to generate answer", e);
            answer = "这次没有拿到回复。下面是已提取的事实。\n\n" + context;
        }

        saveConversation(taskId, question, answer);

        return ChatResponseDTO.builder()
                .answer(answer)
                .evidences(evidenceRefs(taskId))
                .confidence(null)
                .suggestedQuestions(generateSuggestedQuestions(task))
                .build();
    }

    @Override
    public List<ChatMessageDTO> getChatHistory(String taskId) {
        return conversationHistory.getOrDefault(taskId, new ArrayList<>());
    }

    @Override
    public void clearChatHistory(String taskId) {
        conversationHistory.remove(taskId);
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
                你是一个专业的财务分析助手，负责解答关于 %s (%s) 在 %s 期间的财务分析问题。

                当前任务状态：%s
                分析类型：%s

                可用的财务数据和分析结果：
                %s

                只允许使用下面清单里的数字。清单没有的股价、市盈率、市净率、一致预期、目标价，一律回答「未提取」，不要估算。
                不要给买卖建议。每个数字注明它在清单中的科目。
                """,
                task.getCompanyName(),
                task.getCompanyCode(),
                task.getReportPeriod(),
                task.getStatus(),
                task.getAnalysisType(),
                context
        );
    }

    /**
     * 构建任务上下文
     */
    private String buildContext(AnalysisTask task) {
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
        AnalysisSnapshot snapshot = snapshotRepository.findByTaskId(task.getTaskId()).orElse(null);
        if (snapshot == null) {
            context.append("\n分析结果不存在。所有数字回答未提取。\n");
            return context.toString();
        }
        context.append("\n## 指标快照\n");
        context.append(snapshot.getMetricsJson() == null ? "未提取\n" : snapshot.getMetricsJson());
        context.append("\n## 异常\n");
        context.append(snapshot.getAnomaliesJson() == null ? "未提取\n" : snapshot.getAnomaliesJson());
        context.append("\n## 估值\n");
        context.append(snapshot.getValuationJson() == null ? "未提取\n" : snapshot.getValuationJson());
        context.append("\n## 证据\n");
        List<Evidence> evidence = evidenceRepository.findByTaskIdOrderByFieldId(task.getTaskId());
        int shown = 0;
        for (Evidence row : evidence) {
            if (row.getCellRef() != null && row.getCellRef().startsWith("prior")) {
                continue;
            }
            context.append("- ").append(row.getFieldName())
                    .append(" 第").append(row.getPage() == null ? "-" : row.getPage()).append("页 ")
                    .append(row.getValue()).append(" ").append(row.getUnit() == null ? "" : row.getUnit())
                    .append(" ").append(row.getSnippet() == null ? "" : row.getSnippet())
                    .append("\n");
            if (++shown >= 40) {
                break;
            }
        }
        return context.toString();
    }

    private String guardNumbers(String answer, String context) {
        if (answer == null || answer.isBlank()) {
            return "未提取。";
        }
        Matcher matcher = NUMBER.matcher(answer);
        while (matcher.find()) {
            String raw = matcher.group();
            String digits = raw.replace(",", "").replace("，", "");
            if (digits.matches("(?:19|20)\\d{2}") || digits.matches("\\d{1,2}")) {
                continue;
            }
            if (!context.contains(raw) && !context.contains(digits)) {
                return "未提取。回答里出现了快照没有的数字 " + raw + "，已丢弃。\n\n" + context;
            }
        }
        return answer;
    }

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
    private void saveConversation(String taskId, String question, String answer) {
        List<ChatMessageDTO> history = conversationHistory.computeIfAbsent(taskId, k -> new ArrayList<>());

        // 保存用户消息
        history.add(ChatMessageDTO.builder()
                .messageId(UUID.randomUUID().toString())
                .taskId(taskId)
                .role("user")
                .content(question)
                .createdAt(LocalDateTime.now())
                .build());

        // 保存AI回复
        history.add(ChatMessageDTO.builder()
                .messageId(UUID.randomUUID().toString())
                .taskId(taskId)
                .role("assistant")
                .content(answer)
                .createdAt(LocalDateTime.now())
                .build());
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

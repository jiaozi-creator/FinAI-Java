package com.finai.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finai.config.LlmRuntime;
import com.finai.model.AnalysisTask;
import com.finai.model.dto.AnomalySignalDTO;
import com.finai.model.dto.FinancialMetricsDTO;
import com.finai.model.dto.ValuationResultDTO;
import com.finai.service.AuditLogService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模型只通过已算好的工具读数。数字对不上工具结果的段落退回程序文本。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillNarrator {

    private static final Pattern NUMBER = Pattern.compile("-?\\d[\\d,，]*(?:\\.\\d+)?");

    private final dev.langchain4j.model.chat.ChatLanguageModel chatLanguageModel;
    private final LlmRuntime llmRuntime;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Value("${finai.agent.max-iterations:6}")
    private int maxIterations;

    public Result narrate(AnalysisTask task,
                          String growth,
                          String profit,
                          String cash,
                          String overall,
                          FinancialMetricsDTO metrics,
                          List<ArticulationCheck> checks,
                          List<AnomalySignalDTO> anomalies,
                          ValuationResultDTO valuation,
                          StatementExtract extract) {
        String skill = readResource("skills/financial-statement-analysis.md");
        String prompt = readResource("prompts/report-narrative.md");
        String skillVersion = sha256(skill);
        String promptVersion = sha256(prompt);
        Result program = new Result(growth, profit, cash, overall, false, 0, promptVersion, skillVersion);
        if ("DEMO01".equals(task.getCompanyCode()) || llmRuntime.isMock()) {
            return program;
        }
        ToolBox tools = new ToolBox(objectMapper, metrics, checks, anomalies, valuation, extract);
        String system = skill + "\n\n" + prompt + """

                你看不到财报原件。需要数据时只输出一行 JSON，不要其他文字：
                {"tool":"statement.metrics"}
                {"tool":"statement.evidence","fieldId":"all"}
                {"tool":"articulation.check"}
                {"tool":"anomaly.rules"}
                {"tool":"valuation.dcf"}
                至少调用一个工具后，再输出 {"growth":"...","profit":"...","cash":"...","overall":"..."}。
                每段以「推论：」开头。工具结果里没有的金额、比率、倍数不要写。
                """;
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(system));
        messages.add(UserMessage.from("开始。先调用工具。"));
        StringBuilder corpus = new StringBuilder();
        int calls = 0;
        boolean calledTool = false;
        int limit = Math.max(1, maxIterations);
        for (int i = 0; i < limit; i++) {
            String answer;
            long started = System.currentTimeMillis();
            try {
                answer = chatLanguageModel.generate(messages).content().text();
            } catch (Exception e) {
                log.warn("Narrative model skipped: {}", e.getMessage());
                auditLogService.logError(task.getTaskId(), "LLM_CALL", e.getMessage());
                return program.withCalls(calls);
            }
            calls++;
            auditLogService.logLLMRequest(task.getTaskId(), llmDetails(skillVersion, promptVersion),
                    llmRuntime.getModel(), transcript(messages), answer, System.currentTimeMillis() - started);
            if (answer == null || answer.contains("模拟的AI回复")) {
                return program.withCalls(calls);
            }
            messages.add(AiMessage.from(answer));
            JsonNode node = firstObject(answer);
            if (node != null && node.hasNonNull("tool")) {
                String name = node.path("tool").asText();
                String fieldId = node.path("fieldId").asText("all");
                long toolStart = System.currentTimeMillis();
                String output = tools.invoke(name, fieldId);
                auditLogService.logToolInvocation(task.getTaskId(), "agent." + name, fieldId, output,
                        System.currentTimeMillis() - toolStart);
                corpus.append(output).append('\n');
                calledTool = true;
                messages.add(UserMessage.from(output));
                continue;
            }
            if (!calledTool) {
                messages.add(UserMessage.from("还没有工具结果。先输出 {\"tool\":\"statement.metrics\"}。"));
                continue;
            }
            if (node == null || !node.has("growth")) {
                messages.add(UserMessage.from("工具已经返回。请只输出表述 JSON。"));
                continue;
            }
            List<BigDecimal> known = numbersIn(corpus.toString());
            String guardedGrowth = guard(node.path("growth").asText(), growth, known);
            String guardedProfit = guard(node.path("profit").asText(), profit, known);
            String guardedCash = guard(node.path("cash").asText(), cash, known);
            String guardedOverall = guard(node.path("overall").asText(), overall, known);
            boolean replaced = !guardedGrowth.equals(node.path("growth").asText())
                    || !guardedProfit.equals(node.path("profit").asText())
                    || !guardedCash.equals(node.path("cash").asText())
                    || !guardedOverall.equals(node.path("overall").asText());
            if (replaced) {
                guardedOverall = guardedOverall + " 数字核验未通过的段落已改回程序表述。";
            }
            return new Result(guardedGrowth, guardedProfit, guardedCash, guardedOverall, true, calls, promptVersion, skillVersion);
        }
        return program.withCalls(calls);
    }

    private String llmDetails(String skillVersion, String promptVersion) {
        return "provider=" + llmRuntime.getProvider()
                + ",model=" + llmRuntime.getModel()
                + ",temperature=" + llmRuntime.getTemperature()
                + ",skillSha256=" + skillVersion
                + ",promptSha256=" + promptVersion
                + ",leavesMachine=" + llmRuntime.leavesMachine()
                + ",payload=skill+tool-results,rawFile=false";
    }

    private static String guard(String modelText, String fallback, List<BigDecimal> known) {
        if (modelText == null || modelText.isBlank()) {
            return fallback;
        }
        Matcher matcher = NUMBER.matcher(modelText);
        while (matcher.find()) {
            if (!allowed(matcher.group(), known)) {
                return fallback;
            }
        }
        return modelText;
    }

    private static boolean allowed(String raw, List<BigDecimal> known) {
        String digits = raw.replace(",", "").replace("，", "");
        if (digits.matches("(?:19|20)\\d{2}")) {
            return true;
        }
        try {
            BigDecimal value = new BigDecimal(digits);
            for (BigDecimal item : known) {
                if (item.compareTo(value) == 0 || item.compareTo(value.movePointLeft(2)) == 0) {
                    return true;
                }
            }
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static List<BigDecimal> numbersIn(String text) {
        List<BigDecimal> values = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            try {
                values.add(new BigDecimal(matcher.group().replace(",", "").replace("，", "")));
            } catch (NumberFormatException ignored) {
                // 跳过无法解析的片段
            }
        }
        return values;
    }

    private JsonNode firstObject(String answer) {
        int start = answer.indexOf('{');
        int end = answer.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(answer.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }
    }

    private static String transcript(List<ChatMessage> messages) {
        StringBuilder text = new StringBuilder();
        for (ChatMessage message : messages) {
            text.append(message.type()).append('\n').append(message.text()).append("\n---\n");
        }
        return text.toString();
    }

    private static String readResource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("缺少 " + path, e);
        }
    }

    private static String sha256(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    public record Result(String growth, String profit, String cash, String overall, boolean llmUsed, int llmCalls,
                         String promptVersion, String skillVersion) {
        private Result withCalls(int calls) {
            return new Result(growth, profit, cash, overall, llmUsed, calls, promptVersion, skillVersion);
        }
    }

    private record ToolBox(ObjectMapper objectMapper, FinancialMetricsDTO metrics, List<ArticulationCheck> checks,
                           List<AnomalySignalDTO> anomalies, ValuationResultDTO valuation, StatementExtract extract) {
        String invoke(String name, String fieldId) {
            try {
                return switch (name) {
                    case "statement.metrics" -> objectMapper.writeValueAsString(metrics);
                    case "statement.evidence" -> objectMapper.writeValueAsString(evidence(fieldId));
                    case "articulation.check" -> objectMapper.writeValueAsString(checks);
                    case "anomaly.rules" -> objectMapper.writeValueAsString(anomalies);
                    case "valuation.dcf" -> valuation == null ? "未计算" : objectMapper.writeValueAsString(valuation);
                    default -> "未知工具: " + name;
                };
            } catch (Exception e) {
                return "工具结果无法序列化";
            }
        }

        private List<ExtractedLine> evidence(String fieldId) {
            if (fieldId == null || fieldId.isBlank() || "all".equals(fieldId)) {
                return extract.getLines();
            }
            return extract.getLines().stream().filter(line -> fieldId.equals(line.getFieldId())).toList();
        }
    }
}

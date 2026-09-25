package com.finai.controller;

import com.finai.model.dto.ChatMessageDTO;
import com.finai.model.dto.ChatResponseDTO;
import com.finai.service.ChatAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 智能对话分析控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Tag(name = "智能对话", description = "智能对话分析相关接口")
public class ChatAnalysisController {

    private final ChatAnalysisService chatAnalysisService;

    @PostMapping("/{taskId}/ask")
    @Operation(summary = "提问", description = "向AI提问关于任务的问题")
    public ResponseEntity<ChatResponseDTO> askQuestion(
            @PathVariable String taskId,
            @RequestBody Map<String, String> request) {

        String question = request.get("question");
        log.info("Received question for task {}: {}", taskId, question);

        ChatResponseDTO response = chatAnalysisService.askQuestion(taskId, question);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{taskId}/history")
    @Operation(summary = "获取对话历史", description = "获取指定任务的对话历史")
    public ResponseEntity<List<ChatMessageDTO>> getChatHistory(@PathVariable String taskId) {
        List<ChatMessageDTO> history = chatAnalysisService.getChatHistory(taskId);
        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/{taskId}/history")
    @Operation(summary = "清除对话历史", description = "清除指定任务的对话历史")
    public ResponseEntity<Void> clearChatHistory(@PathVariable String taskId) {
        chatAnalysisService.clearChatHistory(taskId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{taskId}/suggestions")
    @Operation(summary = "获取建议问题", description = "获取建议的问题列表")
    public ResponseEntity<List<String>> getSuggestedQuestions(@PathVariable String taskId) {
        List<String> suggestions = chatAnalysisService.getSuggestedQuestions(taskId);
        return ResponseEntity.ok(suggestions);
    }
}

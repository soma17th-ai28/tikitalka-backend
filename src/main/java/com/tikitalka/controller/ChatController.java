package com.tikitalka.controller;

import com.tikitalka.dto.ChatMessage;
import com.tikitalka.dto.ChatRequest;
import com.tikitalka.dto.ChatResponse;
import com.tikitalka.service.ChatHistoryService;
import com.tikitalka.service.ChatPipelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatHistoryService chatHistoryService;
    private final ChatPipelineService chatPipelineService;

    public ChatController(ChatHistoryService chatHistoryService, ChatPipelineService chatPipelineService) {
        this.chatHistoryService = chatHistoryService;
        this.chatPipelineService = chatPipelineService;
    }

    @GetMapping("/history/{deviceId}")
    public ResponseEntity<List<ChatMessage>> getHistory(@PathVariable String deviceId) {
        log.info("[ChatController] 대화 이력 조회 요청 - deviceId={}", deviceId);
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId는 필수입니다.");
        }
        List<ChatMessage> history = chatHistoryService.getAll(deviceId);
        log.info("[ChatController] 대화 이력 조회 완료 - deviceId={}, count={}", deviceId, history.size());
        return ResponseEntity.ok(history);
    }

    @PostMapping("/message")
    public ResponseEntity<ChatResponse> sendMessage(@RequestBody ChatRequest request) {
        log.info("[ChatController] 메시지 전송 요청 - deviceId={}", request.deviceId());
        if (request.deviceId() == null || request.deviceId().isBlank()) {
            throw new IllegalArgumentException("deviceId는 필수입니다.");
        }
        if (request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("message는 필수입니다.");
        }
        ChatResponse response = chatPipelineService.process(request.deviceId(), request.message());
        log.info("[ChatController] 메시지 처리 완료 - deviceId={}", request.deviceId());
        return ResponseEntity.ok(response);
    }
}

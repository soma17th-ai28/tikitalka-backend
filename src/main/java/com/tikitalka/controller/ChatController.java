package com.tikitalka.controller;

import com.tikitalka.dto.ChatMessage;
import com.tikitalka.dto.ChatRequest;
import com.tikitalka.dto.ChatResponse;
import com.tikitalka.service.ChatHistoryService;
import com.tikitalka.service.ChatPipelineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatHistoryService chatHistoryService;
    private final ChatPipelineService chatPipelineService;

    public ChatController(ChatHistoryService chatHistoryService, ChatPipelineService chatPipelineService) {
        this.chatHistoryService = chatHistoryService;
        this.chatPipelineService = chatPipelineService;
    }

    @GetMapping("/history/{deviceId}")
    public ResponseEntity<List<ChatMessage>> getHistory(@PathVariable String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("deviceId는 필수입니다.");
        }
        return ResponseEntity.ok(chatHistoryService.getAll(deviceId));
    }

    @PostMapping("/message")
    public ResponseEntity<ChatResponse> sendMessage(@RequestBody ChatRequest request) {
        if (request.deviceId() == null || request.deviceId().isBlank()) {
            throw new IllegalArgumentException("deviceId는 필수입니다.");
        }
        if (request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("message는 필수입니다.");
        }
        return ResponseEntity.ok(chatPipelineService.process(request.deviceId(), request.message()));
    }
}

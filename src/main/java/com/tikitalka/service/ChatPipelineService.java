package com.tikitalka.service;

import com.tikitalka.client.AiServiceClient;
import com.tikitalka.dto.AiServiceRequest;
import com.tikitalka.dto.AiServiceResponse;
import com.tikitalka.dto.ChatMessage;
import com.tikitalka.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ChatPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ChatPipelineService.class);

    private final ChatHistoryService chatHistoryService;
    private final AiServiceClient aiServiceClient;

    public ChatPipelineService(ChatHistoryService chatHistoryService,
                                AiServiceClient aiServiceClient) {
        this.chatHistoryService = chatHistoryService;
        this.aiServiceClient = aiServiceClient;
    }

    public ChatResponse process(String deviceId, String userMessage) {
        log.info("[ChatPipeline] 파이프라인 시작 - deviceId={}", deviceId);

        log.info("[ChatPipeline] AI 서비스 호출 시작 - deviceId={}", deviceId);
        AiServiceRequest aiRequest = new AiServiceRequest(deviceId, userMessage);
        AiServiceResponse aiResponse = aiServiceClient.call(aiRequest);
        log.info("[ChatPipeline] AI 서비스 호출 완료 - deviceId={}", deviceId);

        LocalDateTime now = LocalDateTime.now();

        ChatMessage userMsg = new ChatMessage(deviceId, "user", userMessage, now, null);
        ChatMessage assistantMsg = new ChatMessage(
                deviceId, "assistant", aiResponse.reply(), now,
                aiResponse.suggestedQuestion()
        );

        log.info("[ChatPipeline] 메시지 저장 요청(비동기) - deviceId={}", deviceId);
        chatHistoryService.saveMessages(userMsg, assistantMsg);

        log.info("[ChatPipeline] 파이프라인 완료 - deviceId={}", deviceId);
        return new ChatResponse(
                "assistant",
                aiResponse.reply(),
                aiResponse.suggestedQuestion(),
                now
        );
    }
}

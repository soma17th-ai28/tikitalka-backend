package com.tikitalka.service;

import com.tikitalka.client.AiServiceClient;
import com.tikitalka.dto.AiServiceRequest;
import com.tikitalka.dto.AiServiceResponse;
import com.tikitalka.dto.ChatMessage;
import com.tikitalka.dto.ChatResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ChatPipelineService {

    private final ChatHistoryService chatHistoryService;
    private final AiServiceClient aiServiceClient;

    public ChatPipelineService(ChatHistoryService chatHistoryService,
                                AiServiceClient aiServiceClient) {
        this.chatHistoryService = chatHistoryService;
        this.aiServiceClient = aiServiceClient;
    }

    public ChatResponse process(String deviceId, String userMessage) {
        AiServiceRequest aiRequest = new AiServiceRequest(deviceId, userMessage);
        AiServiceResponse aiResponse = aiServiceClient.call(aiRequest);

        LocalDateTime now = LocalDateTime.now();

        ChatMessage userMsg = new ChatMessage(deviceId, "user", userMessage, now, null);
        ChatMessage assistantMsg = new ChatMessage(
                deviceId, "assistant", aiResponse.reply(), now,
                aiResponse.suggestedQuestion()
        );

        chatHistoryService.saveMessages(userMsg, assistantMsg);

        return new ChatResponse(
                "assistant",
                aiResponse.reply(),
                aiResponse.suggestedQuestion(),
                now
        );
    }
}

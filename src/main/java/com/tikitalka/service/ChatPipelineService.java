package com.tikitalka.service;

import com.tikitalka.client.AiServiceClient;
import com.tikitalka.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatPipelineService {

    private final ChatHistoryService chatHistoryService;
    private final ContextAssemblyService contextAssemblyService;
    private final AiServiceClient aiServiceClient;

    @Value("${chat.max-context-messages:40}")
    private int maxContextMessages;

    public ChatPipelineService(ChatHistoryService chatHistoryService,
                                ContextAssemblyService contextAssemblyService,
                                AiServiceClient aiServiceClient) {
        this.chatHistoryService = chatHistoryService;
        this.contextAssemblyService = contextAssemblyService;
        this.aiServiceClient = aiServiceClient;
    }

    public ChatResponse process(String deviceId, String userMessage) {
        List<ChatMessage> history = chatHistoryService.getRecent(deviceId, maxContextMessages);
        AiServiceRequest aiRequest = contextAssemblyService.assemble(history, userMessage);
        AiServiceResponse aiResponse = aiServiceClient.call(aiRequest);

        LocalDateTime now = LocalDateTime.now();

        ChatMessage userMsg = new ChatMessage(deviceId, "user", userMessage, now, null, null, null, null);
        ChatMessage assistantMsg = new ChatMessage(
                deviceId, "assistant", aiResponse.content(), now,
                aiResponse.type(), aiResponse.title(),
                aiResponse.relatedQuestions(), aiResponse.sources()
        );

        chatHistoryService.saveMessages(userMsg, assistantMsg);

        return new ChatResponse(
                "assistant",
                aiResponse.type(),
                aiResponse.title(),
                aiResponse.content(),
                aiResponse.relatedQuestions(),
                aiResponse.sources(),
                now
        );
    }
}

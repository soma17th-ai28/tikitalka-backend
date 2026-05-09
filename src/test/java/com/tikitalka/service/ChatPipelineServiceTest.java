package com.tikitalka.service;

import com.tikitalka.client.AiServiceClient;
import com.tikitalka.dto.AiServiceResponse;
import com.tikitalka.dto.ChatMessage;
import com.tikitalka.dto.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatPipelineServiceTest {

    @Mock private ChatHistoryService chatHistoryService;
    @Mock private AiServiceClient aiServiceClient;

    @InjectMocks
    private ChatPipelineService chatPipelineService;

    @Test
    void process_응답_role이_assistant() {
        when(aiServiceClient.call(any())).thenReturn(new AiServiceResponse("device1", "응답 내용", "관련 질문"));

        ChatResponse response = chatPipelineService.process("device1", "축구 알려줘");

        assertThat(response.role()).isEqualTo("assistant");
    }

    @Test
    void process_AI응답_content_그대로_반환() {
        when(aiServiceClient.call(any())).thenReturn(new AiServiceResponse("device1", "AI 응답 내용", "다음 질문"));

        ChatResponse response = chatPipelineService.process("device1", "질문");

        assertThat(response.content()).isEqualTo("AI 응답 내용");
    }

    @Test
    void process_saveMessages_호출됨() {
        when(aiServiceClient.call(any())).thenReturn(new AiServiceResponse("device1", "일반 응답", null));

        chatPipelineService.process("device1", "질문");

        verify(chatHistoryService, times(1)).saveMessages(any(ChatMessage.class), any(ChatMessage.class));
    }

    @Test
    void process_timestamp_포함() {
        when(aiServiceClient.call(any())).thenReturn(new AiServiceResponse("device1", "응답", "질문"));

        LocalDateTime before = LocalDateTime.now();
        ChatResponse response = chatPipelineService.process("device1", "질문");
        LocalDateTime after = LocalDateTime.now();

        assertThat(response.timestamp()).isNotNull();
        assertThat(response.timestamp()).isBetween(before, after);
    }

    @Test
    void process_suggestedQuestion_응답에_포함() {
        when(aiServiceClient.call(any())).thenReturn(new AiServiceResponse("device1", "응답", "다음 질문"));

        ChatResponse response = chatPipelineService.process("device1", "축구 알려줘");

        assertThat(response.suggestedQuestion()).isEqualTo("다음 질문");
    }

    @Test
    void process_suggestedQuestion_null이면_응답에_null() {
        when(aiServiceClient.call(any())).thenReturn(new AiServiceResponse("device1", "응답", null));

        ChatResponse response = chatPipelineService.process("device1", "날씨 알려줘");

        assertThat(response.suggestedQuestion()).isNull();
    }
}

package com.tikitalka.service;

import com.tikitalka.client.AiServiceClient;
import com.tikitalka.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatPipelineServiceTest {

    @Mock private ChatHistoryService chatHistoryService;
    @Mock private ContextAssemblyService contextAssemblyService;
    @Mock private AiServiceClient aiServiceClient;

    @InjectMocks
    private ChatPipelineService chatPipelineService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(chatPipelineService, "maxContextMessages", 40);
    }

    @Test
    void process_응답_role이_assistant() {
        setupMocks("SOCCER_DOMAIN", "축구 정보", "응답 내용");

        ChatResponse response = chatPipelineService.process("device1", "축구 알려줘");

        assertThat(response.role()).isEqualTo("assistant");
    }

    @Test
    void process_AI응답_content_그대로_반환() {
        setupMocks("SOCCER_DOMAIN", "제목", "AI 응답 내용");

        ChatResponse response = chatPipelineService.process("device1", "질문");

        assertThat(response.content()).isEqualTo("AI 응답 내용");
        assertThat(response.type()).isEqualTo("SOCCER_DOMAIN");
        assertThat(response.title()).isEqualTo("제목");
    }

    @Test
    void process_saveMessages_호출됨() {
        setupMocks("GENERAL", null, "일반 응답");

        chatPipelineService.process("device1", "질문");

        verify(chatHistoryService, times(1)).saveMessages(any(ChatMessage.class), any(ChatMessage.class));
    }

    @Test
    void process_timestamp_포함() {
        setupMocks("SOCCER_DOMAIN", "제목", "응답");

        LocalDateTime before = LocalDateTime.now();
        ChatResponse response = chatPipelineService.process("device1", "질문");
        LocalDateTime after = LocalDateTime.now();

        assertThat(response.timestamp()).isNotNull();
        assertThat(response.timestamp()).isBetween(before, after);
    }

    @Test
    void process_getRecent_maxContextMessages_인자로_호출() {
        setupMocks("GENERAL", null, "응답");

        chatPipelineService.process("device1", "질문");

        verify(chatHistoryService, times(1)).getRecent("device1", 40);
    }

    @Test
    void process_relatedQuestions_응답에_포함() {
        List<String> relatedQs = List.of("챔피언스리그 결과는?", "이번 시즌 득점왕은?");
        when(chatHistoryService.getRecent(anyString(), anyInt())).thenReturn(List.of());
        when(contextAssemblyService.assemble(any(), anyString()))
                .thenReturn(new AiServiceRequest(List.of(), "질문"));
        when(aiServiceClient.call(any()))
                .thenReturn(new AiServiceResponse("SOCCER_DOMAIN", "제목", "응답", relatedQs, List.of()));

        ChatResponse response = chatPipelineService.process("device1", "축구 알려줘");

        assertThat(response.relatedQuestions()).containsExactlyElementsOf(relatedQs);
    }

    @Test
    void process_sources_응답에_포함() {
        List<Source> sources = List.of(new Source("FIFA", "https://fifa.com", "2024-01-01"));
        when(chatHistoryService.getRecent(anyString(), anyInt())).thenReturn(List.of());
        when(contextAssemblyService.assemble(any(), anyString()))
                .thenReturn(new AiServiceRequest(List.of(), "질문"));
        when(aiServiceClient.call(any()))
                .thenReturn(new AiServiceResponse("SOCCER_DOMAIN", "제목", "응답", List.of(), sources));

        ChatResponse response = chatPipelineService.process("device1", "축구 알려줘");

        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).title()).isEqualTo("FIFA");
    }

    @Test
    void process_GENERAL_type_title_null_반환() {
        when(chatHistoryService.getRecent(anyString(), anyInt())).thenReturn(List.of());
        when(contextAssemblyService.assemble(any(), anyString()))
                .thenReturn(new AiServiceRequest(List.of(), "질문"));
        when(aiServiceClient.call(any()))
                .thenReturn(new AiServiceResponse("GENERAL", null, "축구 질문을 해주세요.", List.of(), List.of()));

        ChatResponse response = chatPipelineService.process("device1", "날씨 알려줘");

        assertThat(response.type()).isEqualTo("GENERAL");
        assertThat(response.title()).isNull();
    }

    private void setupMocks(String type, String title, String content) {
        when(chatHistoryService.getRecent(anyString(), anyInt())).thenReturn(List.of());
        when(contextAssemblyService.assemble(any(), anyString()))
                .thenReturn(new AiServiceRequest(List.of(), "질문"));
        when(aiServiceClient.call(any()))
                .thenReturn(new AiServiceResponse(type, title, content, List.of(), List.of()));
    }
}

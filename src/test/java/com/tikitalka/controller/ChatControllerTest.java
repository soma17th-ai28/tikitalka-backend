package com.tikitalka.controller;

import com.google.api.services.sheets.v4.Sheets;
import com.tikitalka.dto.*;
import com.tikitalka.service.ChatHistoryService;
import com.tikitalka.service.ChatPipelineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ChatControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JsonMapper objectMapper;

    private MockMvc mockMvc;

    @MockitoBean
    private ChatHistoryService chatHistoryService;

    @MockitoBean
    private ChatPipelineService chatPipelineService;

    @MockitoBean
    private Sheets sheets;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void getHistory_정상요청_200_반환() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        List<ChatMessage> messages = List.of(
                new ChatMessage("device1", "user", "안녕", now, null, null, null, null),
                new ChatMessage("device1", "assistant", "안녕하세요", now, "SOCCER_DOMAIN", "제목", List.of(), List.of())
        );
        when(chatHistoryService.getAll("device1")).thenReturn(messages);

        mockMvc.perform(get("/api/chat/history/device1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[1].role").value("assistant"));
    }

    @Test
    void getHistory_히스토리_없을때_빈배열_반환() throws Exception {
        when(chatHistoryService.getAll("unknown")).thenReturn(List.of());

        mockMvc.perform(get("/api/chat/history/unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void sendMessage_정상요청_200_반환() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        ChatResponse response = new ChatResponse(
                "assistant", "SOCCER_DOMAIN", "축구 정보",
                "AI 응답입니다.", List.of("관련 질문1"), List.of(), now
        );
        when(chatPipelineService.process(anyString(), anyString())).thenReturn(response);

        ChatRequest request = new ChatRequest("device1", "축구 알려줘");

        mockMvc.perform(post("/api/chat/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("assistant"))
                .andExpect(jsonPath("$.type").value("SOCCER_DOMAIN"))
                .andExpect(jsonPath("$.content").value("AI 응답입니다."));
    }

    @Test
    void sendMessage_deviceId_누락_400_반환() throws Exception {
        ChatRequest request = new ChatRequest("", "질문");

        mockMvc.perform(post("/api/chat/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void sendMessage_message_누락_400_반환() throws Exception {
        ChatRequest request = new ChatRequest("device1", "");

        mockMvc.perform(post("/api/chat/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void sendMessage_GENERAL_응답_null_title_JSON에_미포함() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        ChatResponse response = new ChatResponse(
                "assistant", "GENERAL", null, "축구 관련 질문을 해주세요.", List.of(), List.of(), now
        );
        when(chatPipelineService.process(anyString(), anyString())).thenReturn(response);

        ChatRequest request = new ChatRequest("device1", "날씨 알려줘");

        mockMvc.perform(post("/api/chat/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").doesNotExist());
    }

    @Test
    void sendMessage_응답에_timestamp_포함() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        ChatResponse response = new ChatResponse(
                "assistant", "SOCCER_DOMAIN", "제목", "응답", List.of(), List.of(), now
        );
        when(chatPipelineService.process(anyString(), anyString())).thenReturn(response);

        ChatRequest request = new ChatRequest("device1", "질문");

        mockMvc.perform(post("/api/chat/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void sendMessage_서버_내부오류_500_반환() throws Exception {
        when(chatPipelineService.process(anyString(), anyString()))
                .thenThrow(new RuntimeException("AI 서비스 연결 실패"));

        ChatRequest request = new ChatRequest("device1", "질문");

        mockMvc.perform(post("/api/chat/message")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getHistory_서버_내부오류_500_반환() throws Exception {
        when(chatHistoryService.getAll("device1"))
                .thenThrow(new RuntimeException("Google Sheets 조회 실패"));

        mockMvc.perform(get("/api/chat/history/device1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());
    }
}

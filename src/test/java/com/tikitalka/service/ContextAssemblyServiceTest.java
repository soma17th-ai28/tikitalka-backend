package com.tikitalka.service;

import com.tikitalka.dto.AiServiceRequest;
import com.tikitalka.dto.ChatMessage;
import com.tikitalka.dto.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextAssemblyServiceTest {

    private ContextAssemblyService service;

    @BeforeEach
    void setUp() {
        service = new ContextAssemblyService();
    }

    @Test
    void assemble_빈히스토리_빈messages_반환() {
        AiServiceRequest request = service.assemble(List.of(), "새 질문");

        assertThat(request.messages()).isEmpty();
        assertThat(request.userMessage()).isEqualTo("새 질문");
    }

    @Test
    void assemble_히스토리_messages에_순서대로_포함() {
        LocalDateTime now = LocalDateTime.now();
        List<ChatMessage> history = List.of(
                new ChatMessage("device1", "user", "이전 질문", now, null, null, null, null),
                new ChatMessage("device1", "assistant", "이전 답변", now, null, null, null, null)
        );

        AiServiceRequest request = service.assemble(history, "새 질문");

        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().get(0)).isEqualTo(new Message("user", "이전 질문"));
        assertThat(request.messages().get(1)).isEqualTo(new Message("assistant", "이전 답변"));
        assertThat(request.userMessage()).isEqualTo("새 질문");
    }

    @Test
    void assemble_시스템프롬프트_messages에_미포함() {
        AiServiceRequest request = service.assemble(List.of(), "테스트");

        boolean hasSystemRole = request.messages().stream()
                .anyMatch(m -> "system".equals(m.role()));
        assertThat(hasSystemRole).isFalse();
    }
}

package com.tikitalka.service;

import com.tikitalka.dto.ChatMessage;
import com.tikitalka.repository.ChatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceTest {

    @Mock
    private ChatRepository chatRepository;

    @InjectMocks
    private ChatHistoryService chatHistoryService;

    @Test
    void getAll_레포지토리_결과_그대로_반환() {
        List<ChatMessage> messages = List.of(
                makeMessage("device1", "user", "안녕"),
                makeMessage("device1", "assistant", "안녕하세요")
        );
        when(chatRepository.findAllByDeviceId("device1")).thenReturn(messages);

        List<ChatMessage> result = chatHistoryService.getAll("device1");

        assertThat(result).hasSize(2);
        verify(chatRepository, times(1)).findAllByDeviceId("device1");
    }

    @Test
    void getRecent_최대값_초과시_마지막_N개만_반환() {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            messages.add(makeMessage("device1", "user", "질문" + i));
        }
        when(chatRepository.findAllByDeviceId("device1")).thenReturn(messages);

        List<ChatMessage> result = chatHistoryService.getRecent("device1", 3);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).content()).isEqualTo("질문2");
        assertThat(result.get(2).content()).isEqualTo("질문4");
    }

    @Test
    void getRecent_최대값_이하시_전체_반환() {
        List<ChatMessage> messages = List.of(
                makeMessage("device1", "user", "질문0"),
                makeMessage("device1", "assistant", "답변0")
        );
        when(chatRepository.findAllByDeviceId("device1")).thenReturn(messages);

        List<ChatMessage> result = chatHistoryService.getRecent("device1", 10);

        assertThat(result).hasSize(2);
    }

    @Test
    void saveMessages_레포지토리_append_두번_호출() {
        ChatMessage userMsg = makeMessage("device1", "user", "질문");
        ChatMessage assistantMsg = makeMessage("device1", "assistant", "답변");

        chatHistoryService.saveMessages(userMsg, assistantMsg);

        verify(chatRepository, times(1)).append(userMsg);
        verify(chatRepository, times(1)).append(assistantMsg);
    }

    private ChatMessage makeMessage(String deviceId, String role, String content) {
        return new ChatMessage(deviceId, role, content, LocalDateTime.now(), null, null, null, null);
    }
}

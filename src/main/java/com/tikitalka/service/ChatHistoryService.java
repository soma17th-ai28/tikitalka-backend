package com.tikitalka.service;

import com.tikitalka.dto.ChatMessage;
import com.tikitalka.repository.ChatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);

    private final ChatRepository chatRepository;

    public ChatHistoryService(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    public List<ChatMessage> getAll(String deviceId) {
        log.debug("[ChatHistory] 전체 대화 조회 - deviceId={}", deviceId);
        List<ChatMessage> result = chatRepository.findAllByDeviceId(deviceId);
        log.debug("[ChatHistory] 전체 대화 조회 완료 - deviceId={}, count={}", deviceId, result.size());
        return result;
    }

    public List<ChatMessage> getRecent(String deviceId, int maxMessages) {
        log.debug("[ChatHistory] 최근 대화 조회 - deviceId={}, maxMessages={}", deviceId, maxMessages);
        List<ChatMessage> all = chatRepository.findAllByDeviceId(deviceId);
        if (all.size() <= maxMessages) return all;
        return all.subList(all.size() - maxMessages, all.size());
    }

    @Async
    public void saveMessages(ChatMessage userMessage, ChatMessage assistantMessage) {
        log.debug("[ChatHistory] 메시지 저장 시작 - deviceId={}", userMessage.deviceId());
        chatRepository.append(userMessage);
        chatRepository.append(assistantMessage);
        log.debug("[ChatHistory] 메시지 저장 완료 - deviceId={}", userMessage.deviceId());
    }
}

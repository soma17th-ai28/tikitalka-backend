package com.tikitalka.service;

import com.tikitalka.dto.ChatMessage;
import com.tikitalka.repository.ChatRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatHistoryService {

    private final ChatRepository chatRepository;

    public ChatHistoryService(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    public List<ChatMessage> getAll(String deviceId) {
        return chatRepository.findAllByDeviceId(deviceId);
    }

    public List<ChatMessage> getRecent(String deviceId, int maxMessages) {
        List<ChatMessage> all = chatRepository.findAllByDeviceId(deviceId);
        if (all.size() <= maxMessages) return all;
        return all.subList(all.size() - maxMessages, all.size());
    }

    @Async
    public void saveMessages(ChatMessage userMessage, ChatMessage assistantMessage) {
        chatRepository.append(userMessage);
        chatRepository.append(assistantMessage);
    }
}

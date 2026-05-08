package com.tikitalka.service;

import com.tikitalka.dto.AiServiceRequest;
import com.tikitalka.dto.ChatMessage;
import com.tikitalka.dto.Message;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ContextAssemblyService {

    public AiServiceRequest assemble(List<ChatMessage> history, String userMessage) {
        List<Message> messages = new ArrayList<>();

        for (ChatMessage chatMessage : history) {
            messages.add(new Message(chatMessage.role(), chatMessage.content()));
        }

        return new AiServiceRequest(messages, userMessage);
    }
}

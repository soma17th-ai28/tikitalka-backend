package com.tikitalka.dto;

import java.util.List;

public record AiServiceRequest(List<Message> messages, String userMessage) {}

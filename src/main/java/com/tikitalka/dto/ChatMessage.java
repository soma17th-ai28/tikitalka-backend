package com.tikitalka.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(
        String deviceId,
        String role,
        String content,
        LocalDateTime timestamp,
        String type,
        String title,
        List<String> relatedQuestions,
        List<Source> sources
) {}

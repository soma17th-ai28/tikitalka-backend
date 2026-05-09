package com.tikitalka.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(
        String deviceId,
        String role,
        String content,
        LocalDateTime timestamp,
        String suggestedQuestion
) {}

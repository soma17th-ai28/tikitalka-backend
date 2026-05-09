package com.tikitalka.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(
        String role,
        String content,
        String suggestedQuestion,
        LocalDateTime timestamp
) {}

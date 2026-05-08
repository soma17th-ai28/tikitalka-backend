package com.tikitalka.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(
        String role,
        String type,
        String title,
        String content,
        List<String> relatedQuestions,
        List<Source> sources,
        LocalDateTime timestamp
) {}

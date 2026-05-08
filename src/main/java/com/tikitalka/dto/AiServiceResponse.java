package com.tikitalka.dto;

import java.util.List;

public record AiServiceResponse(
        String type,
        String title,
        String content,
        List<String> relatedQuestions,
        List<Source> sources
) {}

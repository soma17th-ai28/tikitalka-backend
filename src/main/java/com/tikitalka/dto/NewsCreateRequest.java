package com.tikitalka.dto;

import java.time.LocalDateTime;

public record NewsCreateRequest(
        String title,
        String summary,
        String league,
        LocalDateTime publishedAt,
        int hotnessScore,
        String originalContent
) {
}

package com.tikitalka.dto;

import java.time.LocalDateTime;

public record NewsDetailResponse(
        String id,
        String title,
        String summary,
        String league,
        LocalDateTime publishedAt,
        int hotnessScore,
        String originalContent
) {
}

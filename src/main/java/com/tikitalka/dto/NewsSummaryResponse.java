package com.tikitalka.dto;

import java.time.LocalDateTime;

public record NewsSummaryResponse(
        String id,
        String title,
        String summary,
        String league,
        LocalDateTime publishedAt,
        int hotnessScore
) {
}

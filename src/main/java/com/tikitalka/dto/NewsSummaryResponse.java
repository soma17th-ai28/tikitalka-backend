package com.tikitalka.dto;

import java.time.LocalDateTime;

public record NewsSummaryResponse(
        String id,
        String title,
        String summary,
        String tag,
        LocalDateTime publishedAt,
        int hotnessScore,
        String url,
        String source
) {
}

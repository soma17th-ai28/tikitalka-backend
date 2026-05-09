package com.tikitalka.dto;

import java.time.LocalDateTime;

public record NewsDetailResponse(
        String id,
        String title,
        String summary,
        String tag,
        LocalDateTime publishedAt,
        int hotnessScore,
        String originalContent,
        String url,
        String source
) {
}

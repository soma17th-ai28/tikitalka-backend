package com.tikitalka.model;

import java.time.LocalDateTime;

public record RawNews(
        String url,
        String title,
        String source,
        LocalDateTime publishedAt,
        String fullContent,
        String summary, // AI 1차 요약
        String tag,     // AI 1차 태그
        String imageUrl,
        boolean isProcessed,
        boolean isIntegrated
) {
}

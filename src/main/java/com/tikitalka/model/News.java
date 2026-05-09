package com.tikitalka.model;

import java.time.LocalDateTime;

public record News(
        String id,
        String title,
        String summary,
        String league,
        LocalDateTime publishedAt,
        int hotnessScore,
        String originalContent
) {
}

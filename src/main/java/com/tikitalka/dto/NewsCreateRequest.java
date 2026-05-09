package com.tikitalka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record NewsCreateRequest(
        String title,
        String source,
        @JsonProperty("publishedAt")
        String publishedAtStr,
        @JsonProperty("description")
        String summary,
        @JsonProperty("full_text")
        String originalContent,
        String url,
        String tag
) {
}

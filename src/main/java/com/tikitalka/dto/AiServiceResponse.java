package com.tikitalka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiServiceResponse(
        @JsonProperty("session_id") String sessionId,
        String reply,
        @JsonProperty("suggested_question") String suggestedQuestion
) {}

package com.tikitalka.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AiServiceRequest(
        @JsonProperty("session_id") String sessionId,
        String message
) {}

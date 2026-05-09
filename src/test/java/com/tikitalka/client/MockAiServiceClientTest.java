package com.tikitalka.client;

import com.tikitalka.dto.AiServiceRequest;
import com.tikitalka.dto.AiServiceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiServiceClientTest {

    private MockAiServiceClient client;

    @BeforeEach
    void setUp() {
        client = new MockAiServiceClient();
    }

    @Test
    void call_축구_한글키워드_reply_반환() {
        AiServiceRequest request = new AiServiceRequest("device1", "오늘 축구 경기 결과 알려줘");

        AiServiceResponse response = client.call(request);

        assertThat(response.reply()).isNotBlank();
        assertThat(response.suggestedQuestion()).isNotNull();
    }

    @Test
    void call_soccer_영문키워드_suggestedQuestion_반환() {
        AiServiceRequest request = new AiServiceRequest("device1", "Who won the football match?");

        AiServiceResponse response = client.call(request);

        assertThat(response.suggestedQuestion()).isNotNull();
    }

    @Test
    void call_비축구_메시지_reply_반환() {
        AiServiceRequest request = new AiServiceRequest("device1", "오늘 날씨 어때?");

        AiServiceResponse response = client.call(request);

        assertThat(response.reply()).isNotBlank();
    }

    @Test
    void call_비축구_suggestedQuestion_null() {
        AiServiceRequest request = new AiServiceRequest("device1", "오늘 날씨 어때?");

        AiServiceResponse response = client.call(request);

        assertThat(response.suggestedQuestion()).isNull();
    }

    @Test
    void call_sessionId_그대로_반환() {
        AiServiceRequest request = new AiServiceRequest("device1", "월드컵 일정 알려줘");

        AiServiceResponse response = client.call(request);

        assertThat(response.sessionId()).isEqualTo("device1");
    }
}

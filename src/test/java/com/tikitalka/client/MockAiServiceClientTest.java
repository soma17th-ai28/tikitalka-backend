package com.tikitalka.client;

import com.tikitalka.dto.AiServiceRequest;
import com.tikitalka.dto.AiServiceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiServiceClientTest {

    private MockAiServiceClient client;

    @BeforeEach
    void setUp() {
        client = new MockAiServiceClient();
    }

    @Test
    void call_축구_한글키워드_SOCCER_DOMAIN_반환() {
        AiServiceRequest request = new AiServiceRequest(List.of(), "오늘 축구 경기 결과 알려줘");

        AiServiceResponse response = client.call(request);

        assertThat(response.type()).isEqualTo("SOCCER_DOMAIN");
        assertThat(response.content()).isNotBlank();
        assertThat(response.relatedQuestions()).isNotEmpty();
    }

    @Test
    void call_soccer_영문키워드_SOCCER_DOMAIN_반환() {
        AiServiceRequest request = new AiServiceRequest(List.of(), "Who won the football match?");

        AiServiceResponse response = client.call(request);

        assertThat(response.type()).isEqualTo("SOCCER_DOMAIN");
    }

    @Test
    void call_비축구_메시지_GENERAL_반환() {
        AiServiceRequest request = new AiServiceRequest(List.of(), "오늘 날씨 어때?");

        AiServiceResponse response = client.call(request);

        assertThat(response.type()).isEqualTo("GENERAL");
        assertThat(response.content()).isNotBlank();
    }

    @Test
    void call_SOCCER_DOMAIN_title_필드_존재() {
        AiServiceRequest request = new AiServiceRequest(List.of(), "월드컵 일정 알려줘");

        AiServiceResponse response = client.call(request);

        assertThat(response.title()).isNotNull();
    }

    @Test
    void call_GENERAL_title_null() {
        AiServiceRequest request = new AiServiceRequest(List.of(), "오늘 뭐 먹지?");

        AiServiceResponse response = client.call(request);

        assertThat(response.title()).isNull();
    }
}

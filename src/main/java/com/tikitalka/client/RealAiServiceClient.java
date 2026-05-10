package com.tikitalka.client;

import com.tikitalka.dto.AiServiceRequest;
import com.tikitalka.dto.AiServiceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "ai.service.mock", havingValue = "false")
public class RealAiServiceClient implements AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RealAiServiceClient.class);

    private final WebClient webClient;

    public RealAiServiceClient(WebClient.Builder webClientBuilder,
                                @Value("${ai.service.url}") String aiServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(aiServiceUrl).build();
        log.info("[AiServiceClient] AI 서비스 URL 설정 완료 - url={}", aiServiceUrl);
    }

    @Override
    public AiServiceResponse call(AiServiceRequest request) {
        log.info("[AiServiceClient] AI 서비스 호출 - sessionId={}", request.sessionId());
        AiServiceResponse response = webClient.post()
                .uri("/chat")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AiServiceResponse.class)
                .timeout(Duration.ofSeconds(90))
                .block();
        log.info("[AiServiceClient] AI 서비스 응답 수신 완료 - sessionId={}", request.sessionId());
        return response;
    }
}

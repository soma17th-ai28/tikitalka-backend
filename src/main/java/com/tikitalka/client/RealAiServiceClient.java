package com.tikitalka.client;

import com.tikitalka.dto.AiServiceRequest;
import com.tikitalka.dto.AiServiceResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@ConditionalOnProperty(name = "ai.service.mock", havingValue = "false")
public class RealAiServiceClient implements AiServiceClient {

    private final WebClient webClient;

    public RealAiServiceClient(WebClient.Builder webClientBuilder,
                                @Value("${ai.service.url}") String aiServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(aiServiceUrl).build();
    }

    @Override
    public AiServiceResponse call(AiServiceRequest request) {
        return webClient.post()
                .uri("/ai/chat")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(AiServiceResponse.class)
                .block();
    }
}

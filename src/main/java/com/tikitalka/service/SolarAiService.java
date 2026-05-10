package com.tikitalka.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class SolarAiService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${external.solar-api.key}")
    private String solarApiKey;

    @Value("${external.solar-api.base-url}")
    private String solarApiBaseUrl;

    public SolarAiService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl(solarApiBaseUrl).build();
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> analyzeNews(String title, String content) {
        String prompt = String.format(
                "You are a professional football editor. Analyze the following news and respond in JSON format.\n" +
                "Title: %s\nContent: %s\n\n" +
                "Requirements:\n" +
                "1. summary: A 3-sentence summary in Korean.\n" +
                "2. tag: Choose one most relevant tag from [epl, laliga, bundesliga, serie-a, ucl, k-league, etc].\n" +
                "3. ai_score: A hotness score between 1 and 100 based on the news impact.\n\n" +
                "Respond ONLY with valid JSON.", title, content.substring(0, Math.min(content.length(), 3000)));

        Map<String, Object> requestBody = Map.of(
                "model", "solar-1-mini-chat",
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ),
                "response_format", Map.of("type", "json_object")
        );

        Map<String, Object> response = webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + solarApiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response != null && response.containsKey("choices")) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            String aiResponseJson = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
            try {
                return objectMapper.readValue(aiResponseJson, Map.class);
            } catch (JsonProcessingException e) {
                return Map.of();
            }
        }
        return Map.of();
    }

    public List<Map<String, Object>> analyzeNewsBatch(List<Map<String, Object>> newsMetadataList) {
        if (newsMetadataList.isEmpty()) return List.of();

        String newsListJson;
        try {
            newsListJson = objectMapper.writeValueAsString(newsMetadataList);
        } catch (JsonProcessingException e) {
            return List.of();
        }

        String prompt = String.format(
                "You are a professional football chief editor. Analyze today's news list and merge related articles into single 'events'.\n" +
                "For each event, provide a unified summary, a representative tag, and a hotnessScore (1-100).\n" +
                "Scoring Criteria:\n" +
                "- Base Impact: AI's judgment on the event's importance.\n" +
                "- Coverage Bonus: Higher score if reported by many sources.\n" +
                "- League Priority: EPL and UCL get +15 points.\n\n" +
                "News List:\n%s\n\n" +
                "Respond ONLY with a JSON array of objects: [{\"title\": \"...\", \"summary\": \"...\", \"tag\": \"...\", \"hotnessScore\": 85, \"sources\": \"BBC, Sky Sports\", \"representativeUrl\": \"...\"}]",
                newsListJson);

        Map<String, Object> requestBody = Map.of(
                "model", "solar-1-mini-chat",
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                ),
                "response_format", Map.of("type", "json_object")
        );

        Map<String, Object> response = webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + solarApiKey)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response != null && response.containsKey("choices")) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            String aiResponseJson = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
            try {
                // The AI might wrap the array in an object, let's try to handle both
                Map<String, Object> resultMap = objectMapper.readValue(aiResponseJson, Map.class);
                if (resultMap.containsKey("events")) {
                    return (List<Map<String, Object>>) resultMap.get("events");
                }
                // If it returned a raw array (as requested in some prompts)
                return objectMapper.readValue(aiResponseJson, List.class);
            } catch (Exception e) {
                // Fallback: try to parse as list directly if map fails
                try {
                    return objectMapper.readValue(aiResponseJson, List.class);
                } catch (Exception e2) {
                    return List.of();
                }
            }
        }
        return List.of();
    }
}

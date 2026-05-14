package com.tikitalka.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class SolarAiService {

    private static final Logger log = LoggerFactory.getLogger(SolarAiService.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${external.solar-api.key}")
    private String solarApiKey;

    @Value("${external.solar-api.base-url}")
    private String solarApiBaseUrl;

    public SolarAiService(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> analyzeNews(String title, String content) {
        log.info("[SolarAI] 단일 기사 분석 시작 - title={}", title);
        
        String prompt = "너는 노련한 축구 기자야. 다음 기사를 분석해서 핵심만 전달해줘.\n\n" +
                "**규칙:**\n" +
                "1. **요약**: 반드시 **딱 1문장**으로만 기사의 핵심을 요약해. **출처나 매체명(예: 출처: BBC, 스카이스포츠 등)은 절대 포함하지 마.**\n" +
                "2. **태그**: 아래 목록 중 하나만 선택.\n" +
                "   - [EPL, LALIGA, BUNDESLIGA, SERIE_A, LIGUE1, CHAMPIONS_LEAGUE, EUROPA_LEAGUE, NOT_FOOTBALL]\n" +
                "3. **말투**: 뉴스 보도 톤(~입니다).\n\n" +
                "제목: " + title + "\n본문: " + content.substring(0, Math.min(content.length(), 1500)) + "\n\n" +
                "응답 JSON 형식: {\"summary\": \"1문장 요약\", \"tag\": \"TAG_NAME\", \"hotnessScore\": 0~100}";

        Map<String, Object> requestBody = Map.of(
                "model", "solar-1-mini-chat",
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "response_format", Map.of("type", "json_object")
        );

        try {
            Map<String, Object> result = webClient.post()
                    .uri(solarApiBaseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + solarApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMinutes(2))
                    .map(response -> {
                        try {
                            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                            String contentStr = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
                            return objectMapper.readValue(contentStr, Map.class);
                        } catch (Exception e) { return Map.<String, Object>of(); }
                    })
                    .block();
            
            log.info("[SolarAI] 단일 기사 분석 완료 - title={}", title);
            return result;
        } catch (Exception e) {
            log.error("[SolarAI] 단일 요약 실패 - error={}, title={}", e.getMessage(), title);
            return Map.of();
        }
    }

    public List<Map<String, Object>> analyzeNewsBatch(List<Map<String, Object>> newsMetadataList, List<Map<String, Object>> existingFeeds) {
        if (newsMetadataList.isEmpty()) return List.of();
        
        String newsListJson;
        String existingFeedsJson;
        try { 
            newsListJson = objectMapper.writeValueAsString(newsMetadataList); 
            existingFeedsJson = objectMapper.writeValueAsString(existingFeeds);
        } catch (JsonProcessingException e) { return List.of(); }

        log.info("[SolarAI] 주제 기반 통합 분석 요청 시작 - candidateCount={}, existingCount={}", newsMetadataList.size(), existingFeeds.size());

        String prompt = "너는 축구 전문 편집장이야. '새 뉴스'들을 분석하여 뉴스 피드를 업데이트해줘.\n\n" +
                "**기존 뉴스 피드 (ID 포함):**\n" + existingFeedsJson + "\n\n" +
                "**새 뉴스 목록:**\n" + newsListJson + "\n\n" +
                "**병합 및 분리 규칙:**\n" +
                "1. **주제별 통합**: 같은 경기(예: 리버풀 vs 첼시)에 관한 모든 소식(선발명단, 경기 중 사건, 스코어, 결과)은 반드시 **하나의 이벤트**로 합쳐.\n" +
                "2. **기존 피드 업데이트**: 새 뉴스가 기존 피드와 같은 경기/사건이라면, **해당 기존 피드의 id를 결과에 포함**시키고 내용을 최신 정보로 업데이트해.\n" +
                "3. **사건 분리**: 서로 다른 경기, 다른 인물, 혹은 연관성이 없는 별개의 사건이라고 판단되면 **절대 합치지 말고 반드시 별개의 이벤트로 분리**해.\n" +
                "4. **제목 선정**: 합쳐진 정보 중 가장 중요하고 최신인 내용을 제목으로 정해 (예: '리버풀 1-0 첼시 (진행중)' 등).\n" +
                "5. **요약**: 통합된 정보를 바탕으로 1~2문장으로 요약해. **요약 본문 안에 '출처: ...' 또는 매체명을 절대 언급하지 마.** 출처는 `sources` 필드에만 기입해.\n\n" +
                "응답 JSON 형식: {\"events\": [{\"id\": \"기존피드id(없으면null)\", \"title\": \"최신제목\", \"summary\": \"요약\", \"tag\": \"TAG_NAME\", \"hotnessScore\": 80, \"sources\": \"매체1, 매체2\", \"representativeUrl\": \"URL\"}]}";

        Map<String, Object> requestBody = Map.of(
                "model", "solar-1-mini-chat",
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "response_format", Map.of("type", "json_object")
        );

        try {
            List<Map<String, Object>> events = webClient.post()
                    .uri(solarApiBaseUrl + "/chat/completions")
                    .header("Authorization", "Bearer " + solarApiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMinutes(10)) 
                    .map(response -> {
                        try {
                            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                            String contentStr = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
                            Map<String, Object> res = objectMapper.readValue(contentStr, Map.class);
                            return (List<Map<String, Object>>) res.get("events");
                        } catch (Exception e) { return List.<Map<String, Object>>of(); }
                    })
                    .block();
            
            log.info("[SolarAI] 주제 기반 통합 분석 완료 - resultCount={}", (events != null ? events.size() : 0));
            return events;
        } catch (Exception e) {
            log.error("[SolarAI] 배치 분석 실패 - error={}", e.getMessage());
            return List.of();
        }
    }
}

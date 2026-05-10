package com.tikitalka.service;

import com.tikitalka.model.RawNews;
import com.tikitalka.repository.RawNewsRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Service
public class NewsIntegrationService {

    private final NewsCollectorService collectorService;
    private final RawNewsRepository rawNewsRepository;

    private final SolarAiService solarAiService;

    private final NewsRepository newsRepository;

    public NewsIntegrationService(NewsCollectorService collectorService, RawNewsRepository rawNewsRepository, SolarAiService solarAiService, NewsRepository newsRepository) {
        this.collectorService = collectorService;
        this.rawNewsRepository = rawNewsRepository;
        this.solarAiService = solarAiService;
        this.newsRepository = newsRepository;
    }

    public void runGlobalScoring() {
        try {
            List<RawNews> allRawNews = rawNewsRepository.findAll();
            LocalDateTime twelveHoursAgo = LocalDateTime.now().minusHours(12);

            List<Map<String, Object>> metadataList = allRawNews.stream()
                    .filter(n -> n.isProcessed() && n.publishedAt().isAfter(twelveHoursAgo))
                    .map(n -> Map.of(
                            "title", n.title(),
                            "summary", n.summary(),
                            "tag", n.tag(),
                            "source", n.source(),
                            "url", n.url()
                    ))
                    .toList();

            if (metadataList.isEmpty()) {
                return;
            }

            List<Map<String, Object>> refinedEvents = solarAiService.analyzeNewsBatch(metadataList);

            for (Map<String, Object> event : refinedEvents) {
                try {
                    processRefinedEvent(event);
                } catch (Exception e) {
                    // 개별 이벤트 처리 실패 시 로그 기록 후 계속 진행
                    System.err.println("Failed to process refined event: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Global scoring failed: " + e.getMessage());
        }
    }

    private void processRefinedEvent(Map<String, Object> event) throws IOException {
        String title = (String) event.get("title");
        String summary = (String) event.get("summary");
        String tag = (String) event.get("tag");
        Object hotnessScoreObj = event.get("hotnessScore");
        int hotnessScore = (hotnessScoreObj instanceof Integer) ? (Integer) hotnessScoreObj : Integer.parseInt(hotnessScoreObj.toString());
        String sources = (String) event.get("sources");
        String url = (String) event.get("representativeUrl");

        List<com.tikitalka.model.News> existingNewsFeed = newsRepository.findAll();
        
        // 제목 유사도나 URL로 기존 뉴스 검색 (간단히 제목 일치로 체크)
        com.tikitalka.model.News existing = existingNewsFeed.stream()
                .filter(n -> n.title().equals(title) || n.url().equals(url))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            // 기존 뉴스 업데이트 (점수 및 소스 갱신)
            com.tikitalka.model.News updatedNews = new com.tikitalka.model.News(
                    existing.id(),
                    title,
                    summary,
                    tag,
                    existing.publishedAt(), // 최초 발행일 유지
                    hotnessScore,
                    "Sources: " + sources,
                    url,
                    sources
            );
            newsRepository.update(updatedNews);
        } else {
            // 신규 뉴스 저장
            com.tikitalka.model.News newNews = new com.tikitalka.model.News(
                    java.util.UUID.randomUUID().toString(),
                    title,
                    summary,
                    tag,
                    LocalDateTime.now(),
                    hotnessScore,
                    "Sources: " + sources,
                    url,
                    sources
            );
            newsRepository.save(newNews);
        }
    }

    public void processRawNewsMetadata() throws IOException {
        List<RawNews> allRawNews = rawNewsRepository.findAll();
        List<RawNews> unprocessedNews = allRawNews.stream()
                .filter(n -> !n.isProcessed())
                .toList();

        for (RawNews news : unprocessedNews) {
            Map<String, Object> aiResult = solarAiService.analyzeNews(news.title(), news.fullContent());

            if (aiResult.isEmpty()) {
                continue;
            }

            RawNews processedNews = new RawNews(
                    news.url(),
                    news.title(),
                    news.source(),
                    news.publishedAt(),
                    news.fullContent(),
                    (String) aiResult.get("summary"),
                    (String) aiResult.get("tag"),
                    true
            );

            rawNewsRepository.update(processedNews);
        }
    }

    public void collectAndStoreRawNews(String query) throws IOException {
        List<Map<String, Object>> articles = collectorService.fetchFootballNews(query);
        List<RawNews> existingNews = rawNewsRepository.findAll();

        for (Map<String, Object> article : articles) {
            String url = (String) article.get("url");

            // URL 기반 중복 체크
            if (existingNews.stream().anyMatch(n -> n.url().equals(url))) {
                continue;
            }

            String title = (String) article.get("title");
            String source = (String) ((Map<String, Object>) article.get("source")).get("name");
            String publishedAtStr = (String) article.get("publishedAt");
            LocalDateTime publishedAt = ZonedDateTime.parse(publishedAtStr).toLocalDateTime();

            // 본문 추출
            String fullContent = collectorService.extractFullContent(url);

            if (fullContent.length() < 100) {
                continue; // 본문이 너무 짧으면 제외
            }

            RawNews rawNews = new RawNews(
                    url, title, source, publishedAt, fullContent, null, null, false
            );

            rawNewsRepository.save(rawNews);
        }
    }
}

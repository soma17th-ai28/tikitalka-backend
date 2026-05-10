package com.tikitalka.service;

import com.tikitalka.model.RawNews;
import com.tikitalka.repository.NewsRepository;
import com.tikitalka.repository.RawNewsRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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

    public void collectAndStoreRawNews(String query) throws IOException {
        System.out.println("\n[NewsPipeline] 🚀 1단계: 뉴스 수집 가동 (" + query + ")");
        List<Map<String, Object>> articles = collectorService.fetchFootballNews(query);
        List<RawNews> existingNews = rawNewsRepository.findAll();

        for (Map<String, Object> article : articles) {
            try {
                String url = (String) article.get("url");
                if (existingNews.stream().anyMatch(n -> n.url().equals(url))) continue;
                String title = (String) article.get("title");
                Map<String, Object> sourceMap = (Map<String, Object>) article.get("source");
                String source = (sourceMap != null) ? (String) sourceMap.get("name") : "Unknown";
                String publishedAtStr = (String) article.get("publishedAt");
                LocalDateTime publishedAt = ZonedDateTime.parse(publishedAtStr).toLocalDateTime();
                String fullContent = collectorService.extractFullContent(url);
                if (fullContent.length() < 100) continue;
                rawNewsRepository.save(new RawNews(url, title, source, publishedAt, fullContent, null, null, false));
                System.out.println("[NewsPipeline] ✅ 수집: " + title);
            } catch (Exception e) { System.err.println("[NewsPipeline] ❌ 수집 실패: " + e.getMessage()); }
        }
        System.out.println("[NewsPipeline] 🏁 1단계 완료");
    }

    public void processRawNewsMetadata() throws IOException {
        System.out.println("\n[NewsPipeline] 🧠 2단계: AI 개별 분석");
        List<RawNews> allRawNews = rawNewsRepository.findAll();
        List<RawNews> unprocessedNews = allRawNews.stream()
                .filter(n -> !n.isProcessed())
                .limit(20) 
                .toList();

        if (unprocessedNews.isEmpty()) return;

        int batchSize = 3; 
        for (int i = 0; i < unprocessedNews.size(); i += batchSize) {
            int end = Math.min(i + batchSize, unprocessedNews.size());
            List<RawNews> batch = unprocessedNews.subList(i, end);
            List<CompletableFuture<RawNews>> futures = batch.stream()
                    .map(news -> CompletableFuture.supplyAsync(() -> {
                        try {
                            Map<String, Object> aiResult = solarAiService.analyzeNews(news.title(), news.fullContent());
                            String tag = (String) aiResult.getOrDefault("tag", "etc");
                            String summary = (String) aiResult.getOrDefault("summary", "");
                            if ("NOT_FOOTBALL".equalsIgnoreCase(tag)) {
                                return new RawNews(news.url(), news.title(), news.source(), news.publishedAt(), news.fullContent(), "NOT_FOOTBALL", "NOT_FOOTBALL", true);
                            }
                            return new RawNews(news.url(), news.title(), news.source(), news.publishedAt(), news.fullContent(), summary, tag, !summary.isEmpty());
                        } catch (Exception e) { return news; }
                    }))
                    .toList();

            List<RawNews> processedBatch = futures.stream().map(CompletableFuture::join).toList();
            for (RawNews pn : processedBatch) {
                if (pn.isProcessed()) {
                    rawNewsRepository.update(pn);
                    System.out.println("[NewsPipeline] ✨ 분석 완료: " + pn.title());
                }
            }
        }
        System.out.println("[NewsPipeline] 🏁 2단계 완료");
    }

    public void runGlobalScoring() {
        try {
            System.out.println("\n[NewsPipeline] 📊 3단계: 통합 스코어링");
            List<RawNews> allRawNews = rawNewsRepository.findAll();
            LocalDateTime timeWindow = LocalDateTime.now().minusHours(72);

            List<Map<String, Object>> metadataList = allRawNews.stream()
                    .filter(n -> n.isProcessed() && !"NOT_FOOTBALL".equals(n.tag()) && n.publishedAt().isAfter(timeWindow))
                    .sorted((n1, n2) -> n2.publishedAt().compareTo(n1.publishedAt()))
                    .map(n -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("title", n.title());
                        map.put("summary", n.summary());
                        map.put("tag", n.tag());
                        map.put("source", n.source());
                        map.put("url", n.url());
                        return map;
                    })
                    .collect(Collectors.toList());

            System.out.println("[NewsPipeline] 🧐 분석 후보: " + metadataList.size() + "건 (최근 72시간 전체)");
            if (metadataList.isEmpty()) return;

            int chunkSize = 10;
            for (int i = 0; i < metadataList.size(); i += chunkSize) {
                int end = Math.min(i + chunkSize, metadataList.size());
                List<Map<String, Object>> chunk = metadataList.subList(i, end);

                // AI에게 보내는 데이터 최소화 (제목, 요약만)
                List<Map<String, Object>> minimalChunk = chunk.stream()
                        .map(n -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("title", n.get("title"));
                            map.put("summary", n.get("summary"));
                            map.put("url", n.get("url")); // URL 복구용으로 유지
                            return map;
                        }).collect(Collectors.toList());

                // 매 청크마다 최신 피드의 ID와 제목을 가져와서 중복 방지 참고용으로 사용
                List<Map<String, Object>> existingFeeds = newsRepository.findAll().stream()
                        .map(n -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("id", n.id());
                            map.put("title", n.title());
                            return map;
                        })
                        .collect(Collectors.toList());

                System.out.println("[NewsPipeline] 🤖 AI 청크 분석 중 (" + (i/chunkSize + 1) + "회차, " + minimalChunk.size() + "건)...");
                List<Map<String, Object>> refinedEvents = solarAiService.analyzeNewsBatch(minimalChunk, existingFeeds);

                if (refinedEvents != null && !refinedEvents.isEmpty()) {
                    for (Map<String, Object> event : refinedEvents) {
                        try { 
                            if (event == null || event.get("title") == null) continue;
                            
                            // representativeUrl 보충
                            if (event.get("representativeUrl") == null) {
                                String eventTitle = event.get("title").toString();
                                chunk.stream()
                                    .filter(c -> eventTitle.contains(c.get("title").toString()) || c.get("title").toString().contains(eventTitle))
                                    .findFirst()
                                    .ifPresent(c -> event.put("representativeUrl", c.get("url")));
                            }
                            
                            processRefinedEvent(event); 
                        } catch (Exception e) { 
                            System.err.println("[NewsPipeline] ❌ 피드 반영 에러: " + e.getMessage());
                        }
                    }
                }
            }
            System.out.println("[NewsPipeline] 🏁 3단계 완료: 전체 뉴스 피드 업데이트 성공");
        } catch (IOException e) { 
            System.err.println("[NewsPipeline] ❌ 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processRefinedEvent(Map<String, Object> event) throws IOException {
        String aiGeneratedId = (String) event.get("id");
        String title = (String) event.get("title");
        String summary = (String) event.get("summary");
        String tag = (String) event.get("tag");
        Object hotnessScoreObj = event.getOrDefault("hotnessScore", 50);
        int hotnessScore = (hotnessScoreObj instanceof Integer) ? (Integer) hotnessScoreObj : Integer.parseInt(hotnessScoreObj.toString());
        String sources = (String) event.get("sources");
        String url = (String) event.get("representativeUrl");

        String originalContent = "Sources: " + sources; 
        if (url != null) {
            List<RawNews> allRaw = rawNewsRepository.findAll();
            originalContent = allRaw.stream()
                    .filter(rn -> url.equals(rn.url()))
                    .map(RawNews::fullContent)
                    .findFirst()
                    .orElse("Sources: " + sources);
        }

        List<com.tikitalka.model.News> feed = newsRepository.findAll();
        
        // 1. AI가 반환한 ID로 찾기
        // 2. 제목이나 URL로 찾기 (백업)
        com.tikitalka.model.News existing = feed.stream()
                .filter(n -> (aiGeneratedId != null && !"null".equals(aiGeneratedId) && n.id().equals(aiGeneratedId)) 
                          || n.title().equals(title) 
                          || (url != null && n.url().equals(url)))
                .findFirst().orElse(null);

        if (existing != null) {
            newsRepository.update(new com.tikitalka.model.News(existing.id(), title, summary, tag, existing.publishedAt(), hotnessScore, originalContent, url, sources));
            System.out.println("[NewsPipeline] 🔄 업데이트: " + title);
        } else {
            newsRepository.save(new com.tikitalka.model.News(java.util.UUID.randomUUID().toString(), title, summary, tag, LocalDateTime.now(), hotnessScore, originalContent, url, sources));
            System.out.println("[NewsPipeline] ✨ 신규 추가: " + title);
        }
    }
}

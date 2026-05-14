package com.tikitalka.service;

import com.tikitalka.model.RawNews;
import com.tikitalka.repository.NewsRepository;
import com.tikitalka.repository.RawNewsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(NewsIntegrationService.class);
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
        log.info("[NewsPipeline] 1단계: 뉴스 수집 시작 - query={}", query);
        List<Map<String, Object>> articles = collectorService.fetchFootballNews(query);
        List<RawNews> existingNews = rawNewsRepository.findAll();

        int count = 0;
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
                String imageUrl = (String) article.get("urlToImage");
                rawNewsRepository.save(new RawNews(url, title, source, publishedAt, fullContent, null, null, imageUrl, false, false));
                count++;
            } catch (Exception e) { 
                log.error("[NewsPipeline] 뉴스 수집 실패 - error={}, url={}", e.getMessage(), article.get("url")); 
            }
        }
        log.info("[NewsPipeline] 1단계: 뉴스 수집 완료 - newArticles={}", count);
    }

    public void processRawNewsMetadata() throws IOException {
        log.info("[NewsPipeline] 2단계: AI 개별 분석 시작");
        List<RawNews> allRawNews = rawNewsRepository.findAll();
        List<RawNews> unprocessedNews = allRawNews.stream()
                .filter(n -> !n.isProcessed())
                .sorted((n1, n2) -> n2.publishedAt().compareTo(n1.publishedAt())) // 최신순 정렬
                .limit(20) 
                .toList();

        if (unprocessedNews.isEmpty()) {
            log.info("[NewsPipeline] 분석할 뉴스가 없습니다.");
            return;
        }

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
                                return new RawNews(news.url(), news.title(), news.source(), news.publishedAt(), news.fullContent(), "NOT_FOOTBALL", "NOT_FOOTBALL", news.imageUrl(), true, false);
                            }
                            return new RawNews(news.url(), news.title(), news.source(), news.publishedAt(), news.fullContent(), summary, tag, news.imageUrl(), !summary.isEmpty(), false);
                        } catch (Exception e) { return news; }
                    }))
                    .toList();

            List<RawNews> processedBatch = futures.stream().map(CompletableFuture::join).toList();
            for (RawNews pn : processedBatch) {
                if (pn.isProcessed()) {
                    rawNewsRepository.update(pn);
                    log.info("[NewsPipeline] AI 개별 분석 완료 - title={}", pn.title());
                }
            }
        }
        log.info("[NewsPipeline] 2단계: AI 개별 분석 완료");
    }

    public void runGlobalScoring() {
        try {
            log.info("[NewsPipeline] 3단계: 주제 기반 통합 스코어링 시작");
            List<RawNews> allRawNews = rawNewsRepository.findAll();
            LocalDateTime timeWindow = LocalDateTime.now().minusHours(72);

            // 아직 통합되지 않은(!isIntegrated) 뉴스만 필터링
            List<Map<String, Object>> metadataList = allRawNews.stream()
                    .filter(n -> n.isProcessed() && !"NOT_FOOTBALL".equals(n.tag()) && !n.isIntegrated() && n.publishedAt().isAfter(timeWindow))
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

            log.info("[NewsPipeline] 분석 후보 선정 완료 - candidateCount={} (72시간 이내, 미통합 건)", metadataList.size());
            if (metadataList.isEmpty()) return;

            // 기존 피드 한 번만 조회
            List<com.tikitalka.model.News> currentFeeds = newsRepository.findAll();

            int chunkSize = 10;
            for (int i = 0; i < metadataList.size(); i += chunkSize) {
                int end = Math.min(i + chunkSize, metadataList.size());
                List<Map<String, Object>> chunk = metadataList.subList(i, end);

                List<Map<String, Object>> existingFeedsForAi = currentFeeds.stream()
                        .map(n -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("id", n.id());
                            map.put("title", n.title());
                            return map;
                        })
                        .collect(Collectors.toList());

                log.info("[NewsPipeline] AI 청크 분석 요청 - chunkIndex={}, chunkSize={}", (i/chunkSize + 1), chunk.size());
                List<Map<String, Object>> refinedEvents = solarAiService.analyzeNewsBatch(chunk, existingFeedsForAi);

                if (refinedEvents != null && !refinedEvents.isEmpty()) {
                    for (Map<String, Object> event : refinedEvents) {
                        try { 
                            if (event == null || event.get("title") == null) continue;
                            
                            if (event.get("representativeUrl") == null) {
                                String eventTitle = event.get("title").toString();
                                chunk.stream()
                                    .filter(c -> eventTitle.contains(c.get("title").toString()) || c.get("title").toString().contains(eventTitle))
                                    .findFirst()
                                    .ifPresent(c -> event.put("representativeUrl", c.get("url")));
                            }
                            
                            processRefinedEvent(event, currentFeeds, allRawNews); 
                        } catch (Exception e) { 
                            log.error("[NewsPipeline] 피드 반영 에러 - title={}, error={}", event.get("title"), e.getMessage());
                        }
                    }
                }

                // 처리된 청크의 뉴스들을 isIntegrated = true로 마킹
                for (Map<String, Object> c : chunk) {
                    String url = (String) c.get("url");
                    allRawNews.stream()
                            .filter(rn -> rn.url().equals(url))
                            .findFirst()
                            .ifPresent(rn -> {
                                try {
                                    rawNewsRepository.update(new RawNews(rn.url(), rn.title(), rn.source(), rn.publishedAt(), rn.fullContent(), rn.summary(), rn.tag(), rn.imageUrl(), rn.isProcessed(), true));
                                } catch (IOException e) {
                                    log.error("[NewsPipeline] isIntegrated 업데이트 실패 - url={}, error={}", url, e.getMessage());
                                }
                            });
                }
            }
            log.info("[NewsPipeline] 3단계: 주제 기반 통합 스코어링 완료");
        } catch (IOException e) { 
            log.error("[NewsPipeline] 스코어링 중 오류 발생 - error={}", e.getMessage());
        }
    }

    public void clearRawNews() throws IOException {
        rawNewsRepository.clearAll();
    }

    private void processRefinedEvent(Map<String, Object> event, List<com.tikitalka.model.News> currentFeeds, List<RawNews> allRaw) throws IOException {
        String aiGeneratedId = (String) event.get("id");
        String title = (String) event.get("title");
        String summary = (String) event.get("summary");
        String tag = (String) event.get("tag");
        Object hotnessScoreObj = event.getOrDefault("hotnessScore", 50);
        int hotnessScore = (hotnessScoreObj instanceof Integer) ? (Integer) hotnessScoreObj : Integer.parseInt(hotnessScoreObj.toString());
        String sources = (String) event.get("sources");
        String url = (String) event.get("representativeUrl");

        RawNews representativeRaw = allRaw.stream()
                .filter(rn -> url != null && url.equals(rn.url()))
                .findFirst()
                .orElse(null);

        String originalContent = representativeRaw != null ? representativeRaw.fullContent() : "Sources: " + sources;
        String imageUrl = representativeRaw != null ? representativeRaw.imageUrl() : "";

        com.tikitalka.model.News existing = currentFeeds.stream()
                .filter(n -> (aiGeneratedId != null && !"null".equals(aiGeneratedId) && n.id().equals(aiGeneratedId)) 
                          || n.title().equals(title) 
                          || (url != null && n.url().equals(url)))
                .findFirst().orElse(null);

        if (existing != null) {
            com.tikitalka.model.News updated = new com.tikitalka.model.News(existing.id(), title, summary, tag, existing.publishedAt(), hotnessScore, originalContent, url, sources, imageUrl);
            newsRepository.update(updated);
            // 로컬 리스트 업데이트
            currentFeeds.remove(existing);
            currentFeeds.add(updated);
            log.info("[NewsPipeline] 피드 업데이트 - title={}", title);
        } else {
            String newId = java.util.UUID.randomUUID().toString();
            com.tikitalka.model.News newNode = new com.tikitalka.model.News(newId, title, summary, tag, LocalDateTime.now(), hotnessScore, originalContent, url, sources, imageUrl);
            newsRepository.save(newNode);
            // 로컬 리스트 추가
            currentFeeds.add(newNode);
            log.info("[NewsPipeline] 신규 피드 추가 - title={}", title);
        }
    }
}

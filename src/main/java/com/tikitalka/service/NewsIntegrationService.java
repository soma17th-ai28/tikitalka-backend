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

    public void runGlobalScoring() throws IOException {
        List<RawNews> allRawNews = rawNewsRepository.findAll();
        // 최근 24시간 내의 처리된 뉴스만 대상으로 함 (실제 구현 시 시간 필터링 추가 가능)
        List<Map<String, Object>> metadataList = allRawNews.stream()
                .filter(RawNews::isProcessed)
                .map(n -> Map.of(
                        "title", n.title(),
                        "summary", n.summary(),
                        "tag", n.tag(),
                        "source", n.source(),
                        "url", n.url()
                ))
                .toList();

        List<Map<String, Object>> refinedEvents = solarAiService.analyzeNewsBatch(metadataList);

        for (Map<String, Object> event : refinedEvents) {
            String title = (String) event.get("title");
            String summary = (String) event.get("summary");
            String tag = (String) event.get("tag");
            int hotnessScore = (int) event.get("hotnessScore");
            String sources = (String) event.get("sources");
            String url = (String) event.get("representativeUrl");

            // 기존 뉴스 피드에 동일 제목 혹은 유사 사건이 있는지 확인 로직 필요
            // 여기서는 단순화를 위해 새로운 뉴스 객체로 변환하여 저장
            com.tikitalka.model.News news = new com.tikitalka.model.News(
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

            // 기존 NewsRepository를 사용하여 저장 (이미 있으면 업데이트하는 로직은 Repository 고도화 필요)
            newsRepository.save(news);
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

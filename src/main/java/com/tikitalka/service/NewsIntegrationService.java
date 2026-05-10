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

    public NewsIntegrationService(NewsCollectorService collectorService, RawNewsRepository rawNewsRepository, SolarAiService solarAiService) {
        this.collectorService = collectorService;
        this.rawNewsRepository = rawNewsRepository;
        this.solarAiService = solarAiService;
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

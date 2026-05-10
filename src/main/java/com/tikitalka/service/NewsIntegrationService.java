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

    public NewsIntegrationService(NewsCollectorService collectorService, RawNewsRepository rawNewsRepository) {
        this.collectorService = collectorService;
        this.rawNewsRepository = rawNewsRepository;
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

package com.tikitalka.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class NewsCollectorService {

    private final WebClient webClient;

    @Value("${external.news-api.key}")
    private String newsApiKey;

    @Value("${external.news-api.base-url}")
    private String newsApiBaseUrl;

    public NewsCollectorService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public List<Map<String, Object>> fetchFootballNews(String query) {
        Map<String, Object> response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("newsapi.org")
                        .path("/v2/everything")
                        .queryParam("q", query)
                        .queryParam("language", "en")
                        .queryParam("sortBy", "publishedAt")
                        .queryParam("pageSize", 10)
                        .queryParam("apiKey", newsApiKey)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response != null && "ok".equals(response.get("status"))) {
            return (List<Map<String, Object>>) response.get("articles");
        }
        return new ArrayList<>();
    }

    public String extractFullContent(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(10000)
                    .get();

            // Remove noise
            doc.select("script, style, nav, footer, header, aside, form, iframe").remove();

            Elements paragraphs = doc.select("p");
            StringBuilder content = new StringBuilder();

            for (Element p : paragraphs) {
                String text = p.text().trim();
                if (text.length() >= 40) {
                    content.append(text).append("\n");
                }
            }
            return content.toString();
        } catch (IOException e) {
            return "";
        }
    }
}

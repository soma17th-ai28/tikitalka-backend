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

    private final WebClient webClient = WebClient.create();

    @Value("${external.news-api.key}")
    private String newsApiKey;

    public NewsCollectorService() {
    }

    public List<Map<String, Object>> fetchFootballNews(String query) {
        java.net.URI uri = org.springframework.web.util.UriComponentsBuilder
                .fromUriString("https://newsapi.org/v2/everything")
                .queryParam("q", query)
                .queryParam("language", "en")
                .queryParam("sortBy", "publishedAt")
                .queryParam("pageSize", 30)
                .queryParam("apiKey", newsApiKey)
                .build().toUri();
        
        System.out.println("Fetching news from NewsAPI: " + uri);

        Map<String, Object> response = webClient.get()
                .uri(uri)
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

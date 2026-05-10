package com.tikitalka.service;

import com.tikitalka.dto.NewsDetailResponse;
import com.tikitalka.dto.NewsSummaryResponse;
import com.tikitalka.dto.PageResponse;
import com.tikitalka.model.News;
import com.tikitalka.repository.NewsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NewsService {

    private static final Logger log = LoggerFactory.getLogger(NewsService.class);
    private final NewsRepository newsRepository;

    public NewsService(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

    public PageResponse<NewsSummaryResponse> getNewsFeed(String tag, int page, int size, String sortBy) throws IOException {
        log.info("[NewsFeedAPI] 뉴스 피드 요청 - tag={}, page={}, size={}, sortBy={}", tag, page, size, sortBy);
        
        List<News> allNews = newsRepository.findAll();

        // Filtering
        List<News> filteredNews = allNews.stream()
                .filter(n -> tag == null || tag.isEmpty() || n.tag().equalsIgnoreCase(tag))
                .collect(Collectors.toList());

        // Sorting
        if ("HOT".equalsIgnoreCase(sortBy)) {
            filteredNews.sort(Comparator.comparingInt(News::hotnessScore).reversed());
        } else {
            filteredNews.sort(Comparator.comparing(News::publishedAt).reversed());
        }

        int totalElements = filteredNews.size();
        int start = page * size;
        int end = Math.min(start + size, totalElements);

        if (start >= totalElements) {
            return PageResponse.of(List.of(), page, size, totalElements);
        }

        List<NewsSummaryResponse> content = filteredNews.subList(start, end).stream()
                .map(n -> new NewsSummaryResponse(
                        n.id(), n.title(), n.summary(), n.tag(), n.publishedAt(), n.hotnessScore(), n.url(), n.source()
                ))
                .collect(Collectors.toList());

        log.info("[NewsFeedAPI] 뉴스 피드 응답 완료 - totalElements={}, returnedCount={}", totalElements, content.size());
        return PageResponse.of(content, page, size, totalElements);
    }

    public NewsDetailResponse getNewsDetail(String id) throws IOException {
        log.info("[NewsFeedAPI] 뉴스 상세 조회 요청 - id={}", id);
        return newsRepository.findAll().stream()
                .filter(n -> n.id().equals(id))
                .findFirst()
                .map(n -> {
                    log.info("[NewsFeedAPI] 뉴스 상세 조회 완료 - title={}", n.title());
                    return new NewsDetailResponse(
                        n.id(), n.title(), n.summary(), n.tag(), n.publishedAt(), n.hotnessScore(), n.originalContent(), n.url(), n.source()
                    );
                })
                .orElseThrow(() -> {
                    log.error("[NewsFeedAPI] 뉴스를 찾을 수 없음 - id={}", id);
                    return new RuntimeException("News not found: " + id);
                });
    }

    public void addNews(News news) throws IOException {
        newsRepository.save(news);
    }
}

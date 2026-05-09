package com.tikitalka.service;

import com.tikitalka.dto.NewsDetailResponse;
import com.tikitalka.dto.NewsSummaryResponse;
import com.tikitalka.dto.PageResponse;
import com.tikitalka.model.News;
import com.tikitalka.repository.NewsRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NewsService {

    private final NewsRepository newsRepository;

    public NewsService(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

    public PageResponse<NewsSummaryResponse> getNewsFeed(String league, int page, int size, String sortBy) throws IOException {
        List<News> allNews = newsRepository.findAll();

        // Filtering & Copying to mutable list
        List<News> filteredNews = allNews.stream()
                .filter(n -> league == null || league.isEmpty() || n.league().equalsIgnoreCase(league))
                .collect(Collectors.toList());

        // Sorting
        if ("HOT".equalsIgnoreCase(sortBy)) {
            filteredNews.sort(Comparator.comparingInt(News::hotnessScore).reversed());
        } else {
            filteredNews.sort(Comparator.comparing(News::publishedAt).reversed());
        }

        // Pagination
        int totalElements = filteredNews.size();
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<NewsSummaryResponse> content = filteredNews.subList(fromIndex, toIndex).stream()
                .map(n -> new NewsSummaryResponse(
                        n.id(), n.title(), n.summary(), n.league(), n.publishedAt(), n.hotnessScore()
                ))
                .collect(Collectors.toList());

        return PageResponse.of(content, page, size, totalElements);
    }

    public NewsDetailResponse getNewsDetail(String id) throws IOException {
        return newsRepository.findAll().stream()
                .filter(n -> n.id().equals(id))
                .findFirst()
                .map(n -> new NewsDetailResponse(
                        n.id(), n.title(), n.summary(), n.league(), n.publishedAt(), n.hotnessScore(), n.originalContent()
                ))
                .orElseThrow(() -> new RuntimeException("News not found: " + id));
    }

    public void addNews(News news) throws IOException {
        newsRepository.save(news);
    }
}

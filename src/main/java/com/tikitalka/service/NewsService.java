package com.tikitalka.service;

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

    public PageResponse<News> getNewsFeed(String league, int page, int size, String sortBy) throws IOException {
        List<News> allNews = newsRepository.findAll();

        // Filtering
        List<News> filteredNews = allNews;
        if (league != null && !league.isEmpty()) {
            filteredNews = allNews.stream()
                    .filter(n -> n.league().equalsIgnoreCase(league))
                    .collect(Collectors.toList());
        }

        // Sorting
        if ("HOT".equalsIgnoreCase(sortBy)) {
            filteredNews.sort(Comparator.comparingInt(News::hotnessScore).reversed());
        } else {
            // Default: LATEST
            filteredNews.sort(Comparator.comparing(News::publishedAt).reversed());
        }

        // Pagination
        int totalElements = filteredNews.size();
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<News> pagedNews = filteredNews.subList(fromIndex, toIndex);

        return PageResponse.of(pagedNews, page, size, totalElements);
    }

    public void addNews(News news) throws IOException {
        newsRepository.save(news);
    }
}

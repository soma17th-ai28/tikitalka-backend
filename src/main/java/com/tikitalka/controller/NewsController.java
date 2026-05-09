package com.tikitalka.controller;

import com.tikitalka.dto.NewsDetailResponse;
import com.tikitalka.dto.NewsSummaryResponse;
import com.tikitalka.dto.PageResponse;
import com.tikitalka.service.NewsService;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    public PageResponse<NewsSummaryResponse> getNewsFeed(
            @RequestParam(required = false) String league,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "LATEST") String sort
    ) throws IOException {
        return newsService.getNewsFeed(league, page, size, sort);
    }

    @GetMapping("/{id}")
    public NewsDetailResponse getNewsDetail(@PathVariable String id) throws IOException {
        return newsService.getNewsDetail(id);
    }
}

package com.tikitalka.controller;

import com.tikitalka.dto.NewsCreateRequest;
import com.tikitalka.model.News;
import com.tikitalka.service.NewsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/internal/api/news")
public class InternalNewsController {

    private final NewsService newsService;

    public InternalNewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @PostMapping
    public void addNews(@RequestBody NewsCreateRequest request) throws IOException {
        News news = new News(
                UUID.randomUUID().toString(),
                request.title(),
                request.summary(),
                request.league(),
                request.publishedAt(),
                request.hotnessScore(),
                request.originalContent()
        );
        newsService.addNews(news);
    }
}

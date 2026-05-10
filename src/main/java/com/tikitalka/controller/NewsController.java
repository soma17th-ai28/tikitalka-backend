package com.tikitalka.controller;

import com.tikitalka.dto.NewsDetailResponse;
import com.tikitalka.dto.NewsSummaryResponse;
import com.tikitalka.dto.PageResponse;
import com.tikitalka.service.NewsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private static final Logger log = LoggerFactory.getLogger(NewsController.class);
    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    @GetMapping
    public PageResponse<NewsSummaryResponse> getNewsFeed(
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "LATEST") String sort
    ) throws IOException {
        log.info("[NewsController] 뉴스 피드 조회 요청 - tag={}, page={}, size={}, sort={}", tag, page, size, sort);
        PageResponse<NewsSummaryResponse> response = newsService.getNewsFeed(tag, page, size, sort);
        log.info("[NewsController] 뉴스 피드 조회 완료 - count={}", response.content().size());
        return response;
    }

    @GetMapping("/{id}")
    public NewsDetailResponse getNewsDetail(@PathVariable String id) throws IOException {
        log.info("[NewsController] 뉴스 상세 조회 요청 - id={}", id);
        NewsDetailResponse response = newsService.getNewsDetail(id);
        log.info("[NewsController] 뉴스 상세 조회 완료 - title={}", response.title());
        return response;
    }
}

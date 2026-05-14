package com.tikitalka.controller;

import com.tikitalka.service.NewsIntegrationService;
import com.tikitalka.model.RawNews;
import com.tikitalka.repository.RawNewsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/internal/test/news")
public class NewsTestController {

    private static final Logger log = LoggerFactory.getLogger(NewsTestController.class);
    private final NewsIntegrationService newsIntegrationService;
    private final RawNewsRepository rawNewsRepository;

    public NewsTestController(NewsIntegrationService newsIntegrationService, RawNewsRepository rawNewsRepository) {
        this.newsIntegrationService = newsIntegrationService;
        this.rawNewsRepository = rawNewsRepository;
    }

    @GetMapping("/trigger")
    public String triggerNewsProcess(@RequestParam(defaultValue = "Son Heung-min") String query) throws IOException {
        log.info("[NewsTestController] 뉴스 프로세스 수동 트리거 시작 - query={}", query);
        
        newsIntegrationService.collectAndStoreRawNews(query);
        newsIntegrationService.processRawNewsMetadata();
        newsIntegrationService.runGlobalScoring();
        
        log.info("[NewsTestController] 뉴스 프로세스 수동 트리거 완료");
        return "Process completed. Check server logs for details.";
    }

    @GetMapping("/clear")
    public String clearRawNews() throws IOException {
        log.info("[NewsTestController] RawNews 시트 초기화 요청");
        newsIntegrationService.clearRawNews();
        return "RawNews sheet cleared and header reset.";
    }

    @GetMapping("/raw-count")
    public String getRawCount() throws IOException {
        List<RawNews> all = rawNewsRepository.findAll();
        long processedCount = all.stream().filter(RawNews::isProcessed).count();
        log.info("[NewsTestController] RawNews 상태 조회 - total={}, processed={}", all.size(), processedCount);
        return "Total RawNews: " + all.size() + ", Processed: " + processedCount;
    }
}

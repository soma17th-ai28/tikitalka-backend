package com.tikitalka.controller;

import com.tikitalka.service.NewsIntegrationService;
import com.tikitalka.model.RawNews;
import com.tikitalka.repository.RawNewsRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/internal/test/news")
public class NewsTestController {

    private final NewsIntegrationService newsIntegrationService;
    private final RawNewsRepository rawNewsRepository;

    public NewsTestController(NewsIntegrationService newsIntegrationService, RawNewsRepository rawNewsRepository) {
        this.newsIntegrationService = newsIntegrationService;
        this.rawNewsRepository = rawNewsRepository;
    }

    @GetMapping("/trigger")
    public String triggerNewsProcess(@RequestParam(defaultValue = "Son Heung-min") String query) throws IOException {
        System.out.println("--- Starting News Process ---");
        newsIntegrationService.collectAndStoreRawNews(query);
        System.out.println("--- Processing Metadata ---");
        newsIntegrationService.processRawNewsMetadata();
        System.out.println("--- Running Global Scoring ---");
        newsIntegrationService.runGlobalScoring();
        return "Process completed. Check server logs for details.";
    }

    @GetMapping("/raw-count")
    public String getRawCount() throws IOException {
        List<RawNews> all = rawNewsRepository.findAll();
        return "Total RawNews: " + all.size() + ", Processed: " + all.stream().filter(RawNews::isProcessed).count();
    }
}

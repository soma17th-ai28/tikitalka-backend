package com.tikitalka.scheduler;

import com.tikitalka.service.NewsIntegrationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class NewsScheduler {

    private final NewsIntegrationService newsIntegrationService;

    public NewsScheduler(NewsIntegrationService newsIntegrationService) {
        this.newsIntegrationService = newsIntegrationService;
    }

    // 매 20분마다 실행
    @Scheduled(cron = "0 0/20 * * * *")
    public void scheduleNewsTask() throws IOException {
        // 1. 수집 (5대 리그 + 챔스 + 유로파)
        newsIntegrationService.collectAndStoreRawNews("Premier League OR La Liga OR Bundesliga OR Serie A OR Ligue 1 OR Champions League OR Europa League");
        
        // 2. 개별 메타데이터 추출
        newsIntegrationService.processRawNewsMetadata();
        
        // 3. 글로벌 스코어링 및 병합
        newsIntegrationService.runGlobalScoring();
    }
}

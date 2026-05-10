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

    public PageResponse<NewsSummaryResponse> getNewsFeed(String tag, int page, int size, String sortBy) throws IOException {
        System.out.println("\n[NewsFeedAPI] 📱 뉴스 피드 요청 수신 [태그: " + (tag != null ? tag : "전체") + ", 정렬: " + sortBy + ", 페이지: " + page + "]");
        
        List<News> allNews = newsRepository.findAll();
        System.out.println("[NewsFeedAPI] 📂 DB에서 총 " + allNews.size() + "건의 뉴스 로드 완료");

        // Filtering
        List<News> filteredNews = allNews.stream()
                .filter(n -> tag == null || tag.isEmpty() || n.tag().equalsIgnoreCase(tag))
                .collect(Collectors.toList());
        
        System.out.println("[NewsFeedAPI] 🔍 필터링 결과: " + filteredNews.size() + "건 생존");

        // Sorting
        if ("HOT".equalsIgnoreCase(sortBy)) {
            System.out.println("[NewsFeedAPI] 🔥 화제성(Hotness) 순으로 정렬 중...");
            filteredNews.sort(Comparator.comparingInt(News::hotnessScore).reversed());
        } else {
            System.out.println("[NewsFeedAPI] 🕒 최신순(Latest)으로 정렬 중...");
            filteredNews.sort(Comparator.comparing(News::publishedAt).reversed());
        }

        // Pagination
        int totalElements = filteredNews.size();
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<NewsSummaryResponse> content = filteredNews.subList(fromIndex, toIndex).stream()
                .map(n -> new NewsSummaryResponse(
                        n.id(), n.title(), n.summary(), n.tag(), n.publishedAt(), n.hotnessScore(), n.url(), n.source()
                ))
                .collect(Collectors.toList());

        System.out.println("[NewsFeedAPI] ✅ 응답 생성 완료: " + content.size() + "건 반환 예정 (Total: " + totalElements + ")");
        return PageResponse.of(content, page, size, totalElements);
    }

    public NewsDetailResponse getNewsDetail(String id) throws IOException {
        System.out.println("[NewsFeedAPI] 📄 뉴스 상세 조회 요청 [ID: " + id + "]");
        return newsRepository.findAll().stream()
                .filter(n -> n.id().equals(id))
                .findFirst()
                .map(n -> {
                    System.out.println("[NewsFeedAPI] ✅ 상세 데이터 발견: " + n.title());
                    return new NewsDetailResponse(
                        n.id(), n.title(), n.summary(), n.tag(), n.publishedAt(), n.hotnessScore(), n.originalContent(), n.url(), n.source()
                    );
                })
                .orElseThrow(() -> {
                    System.err.println("[NewsFeedAPI] ❌ 뉴스를 찾을 수 없음 [ID: " + id + "]");
                    return new RuntimeException("News not found: " + id);
                });
    }

    public void addNews(News news) throws IOException {
        newsRepository.save(news);
    }
}

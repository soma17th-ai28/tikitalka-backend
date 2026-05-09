package com.tikitalka.service;

import com.tikitalka.dto.PageResponse;
import com.tikitalka.model.News;
import com.tikitalka.repository.NewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    @Mock
    private NewsRepository newsRepository;

    @InjectMocks
    private NewsService newsService;

    private List<News> sampleNews;

    @BeforeEach
    void setUp() {
        sampleNews = List.of(
                new News("1", "PL News 1", "Summary 1", "PL", LocalDateTime.now().minusHours(2), 10, "Content 1"),
                new News("2", "Bundesliga News 1", "Summary 2", "Bundesliga", LocalDateTime.now().minusHours(1), 50, "Content 2"),
                new News("3", "PL News 2", "Summary 3", "PL", LocalDateTime.now().minusHours(3), 30, "Content 3"),
                new News("4", "ChampionsLeague News 1", "Summary 4", "ChampionsLeague", LocalDateTime.now(), 20, "Content 4")
        );
    }

    @Test
    @DisplayName("리그 필터링이 정상적으로 동작해야 한다")
    void filterByLeague() throws IOException {
        // given
        when(newsRepository.findAll()).thenReturn(sampleNews);

        // when
        PageResponse<News> result = newsService.getNewsFeed("PL", 0, 10, "LATEST");

        // then
        assertThat(result.content()).hasSize(2);
        assertThat(result.content()).allMatch(n -> n.league().equals("PL"));
    }

    @Test
    @DisplayName("최신순 정렬이 정상적으로 동작해야 한다")
    void sortByLatest() throws IOException {
        // given
        when(newsRepository.findAll()).thenReturn(sampleNews);

        // when
        PageResponse<News> result = newsService.getNewsFeed(null, 0, 10, "LATEST");

        // then
        assertThat(result.content().get(0).id()).isEqualTo("4"); // 가장 최근
        assertThat(result.content().get(1).id()).isEqualTo("2");
    }

    @Test
    @DisplayName("화제성순 정렬이 정상적으로 동작해야 한다")
    void sortByHot() throws IOException {
        // given
        when(newsRepository.findAll()).thenReturn(sampleNews);

        // when
        PageResponse<News> result = newsService.getNewsFeed(null, 0, 10, "HOT");

        // then
        assertThat(result.content().get(0).id()).isEqualTo("2"); // Score 50
        assertThat(result.content().get(1).id()).isEqualTo("3"); // Score 30
    }

    @Test
    @DisplayName("페이지네이션이 정상적으로 동작해야 한다")
    void pagination() throws IOException {
        // given
        when(newsRepository.findAll()).thenReturn(sampleNews);

        // when
        PageResponse<News> result = newsService.getNewsFeed(null, 1, 2, "LATEST"); // 1페이지, 사이즈 2

        // then
        assertThat(result.content()).hasSize(2);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(4);
        assertThat(result.totalPages()).isEqualTo(2);
    }
}

package com.tikitalka.repository;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.tikitalka.config.GoogleSheetsProperties;
import com.tikitalka.model.News;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class NewsRepository {

    private final Sheets sheets;
    private final GoogleSheetsProperties properties;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public NewsRepository(Sheets sheets, GoogleSheetsProperties properties) {
        this.sheets = sheets;
        this.properties = properties;
    }

    public List<News> findAll() throws IOException {
        ValueRange response = sheets.spreadsheets().values()
                .get(properties.spreadsheetId(), properties.range())
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }

        return values.stream()
                .map(this::mapToNews)
                .collect(Collectors.toList());
    }

    public void save(News news) throws IOException {
        List<Object> row = mapToRow(news);
        ValueRange body = new ValueRange().setValues(List.of(row));

        sheets.spreadsheets().values()
                .append(properties.spreadsheetId(), properties.range(), body)
                .setValueInputOption("RAW")
                .execute();
    }

    private News mapToNews(List<Object> row) {
        return new News(
                getString(row, 0),
                getString(row, 1),
                getString(row, 2),
                getString(row, 3),
                LocalDateTime.parse(getString(row, 4), FORMATTER),
                Integer.parseInt(getString(row, 5)),
                getString(row, 6)
        );
    }

    private List<Object> mapToRow(News news) {
        return List.of(
                news.id(),
                news.title(),
                news.summary(),
                news.league(),
                news.publishedAt().format(FORMATTER),
                String.valueOf(news.hotnessScore()),
                news.originalContent()
        );
    }

    private String getString(List<Object> row, int index) {
        if (index >= row.size() || row.get(index) == null) {
            return "";
        }
        return row.get(index).toString();
    }
}

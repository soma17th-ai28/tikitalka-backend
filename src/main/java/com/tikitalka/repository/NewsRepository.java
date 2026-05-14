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
    private static final String SHEET_NAME = "News"; // 시트 이름 명시
    private static final String RANGE = "News!A:J";  // 범위 고정

    public NewsRepository(Sheets sheets, GoogleSheetsProperties properties) {
        this.sheets = sheets;
        this.properties = properties;
    }

    public List<News> findAll() throws IOException {
        ValueRange response = sheets.spreadsheets().values()
                .get(properties.spreadsheetId(), RANGE)
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) return new ArrayList<>();

        return values.stream()
                .filter(row -> row.size() >= 5 && !getString(row, 0).equals("id") && !getString(row, 0).isEmpty())
                .map(this::mapToNews)
                .collect(Collectors.toList());
    }

    public void save(News news) throws IOException {
        ensureHeader();
        List<Object> row = mapToRow(news);
        ValueRange body = new ValueRange().setValues(List.of(row));

        sheets.spreadsheets().values()
                .append(properties.spreadsheetId(), RANGE, body)
                .setValueInputOption("RAW")
                .execute();
    }

    public void update(News news) throws IOException {
        ValueRange response = sheets.spreadsheets().values()
                .get(properties.spreadsheetId(), RANGE)
                .execute();
        List<List<Object>> allRows = response.getValues();
        
        if (allRows == null) return;

        int physicalRowIndex = -1;
        for (int i = 0; i < allRows.size(); i++) {
            if (getString(allRows.get(i), 0).equals(news.id())) {
                physicalRowIndex = i + 1;
                break;
            }
        }

        if (physicalRowIndex > 0) {
            String updateRange = SHEET_NAME + "!A" + physicalRowIndex + ":J" + physicalRowIndex;
            ValueRange body = new ValueRange().setValues(List.of(mapToRow(news)));
            sheets.spreadsheets().values()
                    .update(properties.spreadsheetId(), updateRange, body)
                    .setValueInputOption("RAW")
                    .execute();
        }
    }

    private void ensureHeader() throws IOException {
        ValueRange response = sheets.spreadsheets().values()
                .get(properties.spreadsheetId(), SHEET_NAME + "!A1:A1")
                .execute();
        if (response.getValues() == null || response.getValues().isEmpty()) {
            List<Object> header = List.of("id", "title", "summary", "tag", "publishedAt", "hotnessScore", "originalContent", "url", "source", "imageUrl");
            ValueRange headerBody = new ValueRange().setValues(List.of(header));
            sheets.spreadsheets().values()
                    .update(properties.spreadsheetId(), SHEET_NAME + "!A1:J1", headerBody)
                    .setValueInputOption("RAW")
                    .execute();
        }
    }

    private News mapToNews(List<Object> row) {
        String publishedAtStr = getString(row, 4);
        LocalDateTime publishedAt;
        try {
            publishedAt = publishedAtStr.isEmpty() ? LocalDateTime.now() : LocalDateTime.parse(publishedAtStr, FORMATTER);
        } catch (Exception e) {
            publishedAt = LocalDateTime.now();
        }

        return new News(
                getString(row, 0), getString(row, 1), getString(row, 2), getString(row, 3),
                publishedAt, Integer.parseInt(getString(row, 5).isEmpty() ? "0" : getString(row, 5)),
                getString(row, 6), getString(row, 7), getString(row, 8), getString(row, 9)
        );
    }

    private List<Object> mapToRow(News news) {
        return List.of(
                news.id(), news.title(), news.summary(), news.tag(),
                news.publishedAt().format(FORMATTER), String.valueOf(news.hotnessScore()),
                news.originalContent(), news.url(), news.source(),
                news.imageUrl() != null ? news.imageUrl() : ""
        );
    }

    private String getString(List<Object> row, int index) {
        if (index >= row.size() || row.get(index) == null) return "";
        return row.get(index).toString();
    }
}

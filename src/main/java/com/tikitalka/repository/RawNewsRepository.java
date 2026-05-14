package com.tikitalka.repository;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.tikitalka.config.GoogleSheetsProperties;
import com.tikitalka.model.RawNews;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class RawNewsRepository {

    private final Sheets sheets;
    private final GoogleSheetsProperties properties;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String RANGE = "RawNews!A:J";

    public RawNewsRepository(Sheets sheets, GoogleSheetsProperties properties) {
        this.sheets = sheets;
        this.properties = properties;
    }

    public List<RawNews> findAll() throws IOException {
        ValueRange response = sheets.spreadsheets().values()
                .get(properties.spreadsheetId(), RANGE)
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) return new ArrayList<>();

        // 헤더("url")를 가진 행과 빈 행을 제외
        return values.stream()
                .filter(row -> row.size() >= 4 && !getString(row, 0).equals("url") && !getString(row, 0).isEmpty())
                .map(this::mapToRawNews)
                .collect(Collectors.toList());
    }

    public void save(RawNews news) throws IOException {
        ensureHeader();
        List<Object> row = mapToRow(news);
        ValueRange body = new ValueRange().setValues(List.of(row));
        sheets.spreadsheets().values()
                .append(properties.spreadsheetId(), RANGE, body)
                .setValueInputOption("RAW")
                .execute();
    }

    public void update(RawNews news) throws IOException {
        ValueRange response = sheets.spreadsheets().values()
                .get(properties.spreadsheetId(), RANGE)
                .execute();
        List<List<Object>> allRows = response.getValues();
        
        if (allRows == null) return;

        int physicalRowIndex = -1;
        for (int i = 0; i < allRows.size(); i++) {
            if (getString(allRows.get(i), 0).equals(news.url())) {
                physicalRowIndex = i + 1;
                break;
            }
        }

        if (physicalRowIndex > 0) {
            String updateRange = "RawNews!A" + physicalRowIndex + ":J" + physicalRowIndex;
            ValueRange body = new ValueRange().setValues(List.of(mapToRow(news)));
            sheets.spreadsheets().values()
                    .update(properties.spreadsheetId(), updateRange, body)
                    .setValueInputOption("RAW")
                    .execute();
        }
    }

    public void clearAll() throws IOException {
        sheets.spreadsheets().values()
                .clear(properties.spreadsheetId(), "RawNews!A:J", new com.google.api.services.sheets.v4.model.ClearValuesRequest())
                .execute();
        ensureHeader(); // 헤더 즉시 재생성
    }

    private void ensureHeader() throws IOException {
        ValueRange response = sheets.spreadsheets().values()
                .get(properties.spreadsheetId(), "RawNews!A1:A1")
                .execute();
        if (response.getValues() == null || response.getValues().isEmpty()) {
            List<Object> header = List.of("url", "title", "source", "publishedAt", "fullContent", "summary", "tag", "imageUrl", "isProcessed", "isIntegrated");
            ValueRange headerBody = new ValueRange().setValues(List.of(header));
            sheets.spreadsheets().values()
                    .update(properties.spreadsheetId(), "RawNews!A1:J1", headerBody)
                    .setValueInputOption("RAW")
                    .execute();
        }
    }

    private RawNews mapToRawNews(List<Object> row) {
        String publishedAtStr = getString(row, 3);
        LocalDateTime publishedAt;
        try {
            publishedAt = publishedAtStr.isEmpty() ? LocalDateTime.now() : LocalDateTime.parse(publishedAtStr, FORMATTER);
        } catch (Exception e) {
            publishedAt = LocalDateTime.now();
        }

        return new RawNews(
                getString(row, 0), getString(row, 1), getString(row, 2), publishedAt,
                getString(row, 4), getString(row, 5), getString(row, 6),
                getString(row, 7),
                row.size() > 8 && Boolean.parseBoolean(getString(row, 8)),
                row.size() > 9 && Boolean.parseBoolean(getString(row, 9))
        );
    }

    private List<Object> mapToRow(RawNews news) {
        return List.of(
                news.url(), news.title(), news.source(), news.publishedAt().format(FORMATTER),
                news.fullContent(), news.summary() != null ? news.summary() : "",
                news.tag() != null ? news.tag() : "", 
                news.imageUrl() != null ? news.imageUrl() : "",
                String.valueOf(news.isProcessed()),
                String.valueOf(news.isIntegrated())
        );
    }

    private String getString(List<Object> row, int index) {
        if (index >= row.size() || row.get(index) == null) return "";
        return row.get(index).toString();
    }
}

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
    private static final String RANGE = "RawNews!A:H";

    public RawNewsRepository(Sheets sheets, GoogleSheetsProperties properties) {
        this.sheets = sheets;
        this.properties = properties;
    }

    public List<RawNews> findAll() throws IOException {
        ValueRange response = sheets.spreadsheets().values()
                .get(properties.spreadsheetId(), RANGE)
                .execute();

        List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty()) {
            return new ArrayList<>();
        }

        return values.stream()
                .map(this::mapToRawNews)
                .collect(Collectors.toList());
    }

    public void save(RawNews news) throws IOException {
        List<Object> row = mapToRow(news);
        ValueRange body = new ValueRange().setValues(List.of(row));

        sheets.spreadsheets().values()
                .append(properties.spreadsheetId(), RANGE, body)
                .setValueInputOption("RAW")
                .execute();
    }

    public void update(RawNews news) throws IOException {
        List<RawNews> allNews = findAll();
        int rowIndex = -1;
        for (int i = 0; i < allNews.size(); i++) {
            if (allNews.get(i).url().equals(news.url())) {
                rowIndex = i + 1; // 1-based index (assuming no header, or adjust if there is)
                break;
            }
        }

        if (rowIndex != -1) {
            String updateRange = "RawNews!A" + rowIndex + ":H" + rowIndex;
            ValueRange body = new ValueRange().setValues(List.of(mapToRow(news)));
            sheets.spreadsheets().values()
                    .update(properties.spreadsheetId(), updateRange, body)
                    .setValueInputOption("RAW")
                    .execute();
        }
    }

    private RawNews mapToRawNews(List<Object> row) {
        return new RawNews(
                getString(row, 0),
                getString(row, 1),
                getString(row, 2),
                LocalDateTime.parse(getString(row, 3), FORMATTER),
                getString(row, 4),
                getString(row, 5),
                getString(row, 6),
                Boolean.parseBoolean(getString(row, 7))
        );
    }

    private List<Object> mapToRow(RawNews news) {
        return List.of(
                news.url(),
                news.title(),
                news.source(),
                news.publishedAt().format(FORMATTER),
                news.fullContent(),
                news.summary() != null ? news.summary() : "",
                news.tag() != null ? news.tag() : "",
                String.valueOf(news.isProcessed())
        );
    }

    private String getString(List<Object> row, int index) {
        if (index >= row.size() || row.get(index) == null) {
            return "";
        }
        return row.get(index).toString();
    }
}

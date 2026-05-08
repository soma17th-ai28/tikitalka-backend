package com.tikitalka.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.tikitalka.config.GoogleSheetsProperties;
import com.tikitalka.dto.ChatMessage;
import com.tikitalka.dto.Source;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ChatRepository {

    private final Sheets sheets;
    private final GoogleSheetsProperties properties;
    private final ObjectMapper objectMapper;

    public ChatRepository(Sheets sheets, GoogleSheetsProperties properties, ObjectMapper objectMapper) {
        this.sheets = sheets;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void append(ChatMessage message) {
        try {
            String relatedQJson = message.relatedQuestions() != null
                    ? objectMapper.writeValueAsString(message.relatedQuestions()) : "";
            String sourcesJson = message.sources() != null
                    ? objectMapper.writeValueAsString(message.sources()) : "";

            List<Object> row = List.of(
                    message.deviceId(),
                    message.role(),
                    message.content(),
                    message.timestamp().toString(),
                    nullToEmpty(message.type()),
                    nullToEmpty(message.title()),
                    relatedQJson,
                    sourcesJson
            );

            ValueRange body = new ValueRange().setValues(List.of(row));
            sheets.spreadsheets().values()
                    .append(properties.spreadsheetId(), properties.range(), body)
                    .setValueInputOption("RAW")
                    .execute();
        } catch (Exception e) {
            throw new RuntimeException("Google Sheets append 실패", e);
        }
    }

    public List<ChatMessage> findAllByDeviceId(String deviceId) {
        try {
            ValueRange response = sheets.spreadsheets().values()
                    .get(properties.spreadsheetId(), properties.range())
                    .execute();

            List<List<Object>> values = response.getValues();
            if (values == null || values.isEmpty()) return List.of();

            List<ChatMessage> result = new ArrayList<>();
            for (List<Object> row : values) {
                if (row.size() < 4) continue;
                if (!deviceId.equals(row.get(0).toString())) continue;

                String relatedQJson = row.size() > 6 ? row.get(6).toString() : "";
                String sourcesJson = row.size() > 7 ? row.get(7).toString() : "";

                List<String> relatedQuestions = relatedQJson.isBlank() ? null
                        : objectMapper.readValue(relatedQJson, new TypeReference<>() {});
                List<Source> sources = sourcesJson.isBlank() ? null
                        : objectMapper.readValue(sourcesJson, new TypeReference<>() {});

                result.add(new ChatMessage(
                        row.get(0).toString(),
                        row.get(1).toString(),
                        row.get(2).toString(),
                        LocalDateTime.parse(row.get(3).toString()),
                        row.size() > 4 ? emptyToNull(row.get(4).toString()) : null,
                        row.size() > 5 ? emptyToNull(row.get(5).toString()) : null,
                        relatedQuestions,
                        sources
                ));
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Google Sheets 조회 실패", e);
        }
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private String emptyToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}

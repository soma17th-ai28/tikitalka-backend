package com.tikitalka.repository;

import tools.jackson.databind.json.JsonMapper;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.tikitalka.config.GoogleSheetsProperties;
import com.tikitalka.dto.ChatMessage;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ChatRepository {

    private final Sheets sheets;
    private final GoogleSheetsProperties properties;
    private final JsonMapper objectMapper;

    public ChatRepository(
            Sheets sheets,
            GoogleSheetsProperties properties,
            JsonMapper objectMapper
    ) {
        this.sheets = sheets;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void append(ChatMessage message) {
        try {
            List<Object> row = List.of(
                    message.deviceId(),
                    message.role(),
                    message.content(),
                    message.timestamp().toString(),
                    nullToEmpty(message.suggestedQuestion())
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

            if (values == null || values.isEmpty()) {
                return List.of();
            }

            List<ChatMessage> result = new ArrayList<>();

            for (List<Object> row : values) {
                if (row.size() < 4) {
                    continue;
                }

                if (!deviceId.equals(row.get(0).toString())) {
                    continue;
                }

                String suggestedQuestion = row.size() > 4
                        ? emptyToNull(row.get(4).toString())
                        : null;

                result.add(new ChatMessage(
                        row.get(0).toString(),
                        row.get(1).toString(),
                        row.get(2).toString(),
                        LocalDateTime.parse(row.get(3).toString()),
                        suggestedQuestion
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
        return value == null || value.isBlank() ? null : value;
    }
}

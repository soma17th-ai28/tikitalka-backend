package com.tikitalka.repository;

import tools.jackson.databind.json.JsonMapper;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.tikitalka.config.GoogleSheetsProperties;
import com.tikitalka.dto.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRepositoryTest {

    @Mock private Sheets sheets;
    @Mock private Sheets.Spreadsheets spreadsheets;
    @Mock private Sheets.Spreadsheets.Values values;
    @Mock private Sheets.Spreadsheets.Values.Get getRequest;
    @Mock private Sheets.Spreadsheets.Values.Append appendRequest;

    private ChatRepository chatRepository;

    private static final GoogleSheetsProperties PROPERTIES =
            new GoogleSheetsProperties("TestApp", "spreadsheet-id", "/creds.json", "Chat!A:E");

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = new JsonMapper();
        chatRepository = new ChatRepository(sheets, PROPERTIES, jsonMapper);

        when(sheets.spreadsheets()).thenReturn(spreadsheets);
        when(spreadsheets.values()).thenReturn(values);
    }

    // ──────────────────────────────
    // findAllByDeviceId
    // ──────────────────────────────

    @Test
    void findAllByDeviceId_빈시트_빈리스트_반환() throws IOException {
        when(values.get(anyString(), anyString())).thenReturn(getRequest);
        when(getRequest.execute()).thenReturn(new ValueRange().setValues(null));

        assertThat(chatRepository.findAllByDeviceId("device1")).isEmpty();
    }

    @Test
    void findAllByDeviceId_빈rows_빈리스트_반환() throws IOException {
        when(values.get(anyString(), anyString())).thenReturn(getRequest);
        when(getRequest.execute()).thenReturn(new ValueRange().setValues(List.of()));

        assertThat(chatRepository.findAllByDeviceId("device1")).isEmpty();
    }

    @Test
    void findAllByDeviceId_다른_deviceId_제외() throws IOException {
        LocalDateTime now = LocalDateTime.now();
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("device1", "user", "안녕", now.toString(), ""));
        rows.add(List.of("device2", "user", "Hello", now.toString(), ""));

        when(values.get(anyString(), anyString())).thenReturn(getRequest);
        when(getRequest.execute()).thenReturn(new ValueRange().setValues(rows));

        List<ChatMessage> result = chatRepository.findAllByDeviceId("device1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).deviceId()).isEqualTo("device1");
        assertThat(result.get(0).content()).isEqualTo("안녕");
    }

    @Test
    void findAllByDeviceId_빈문자열_suggestedQuestion_null로_변환() throws IOException {
        LocalDateTime now = LocalDateTime.now();
        List<List<Object>> rows = List.of(
                List.of("device1", "user", "안녕", now.toString(), "")
        );

        when(values.get(anyString(), anyString())).thenReturn(getRequest);
        when(getRequest.execute()).thenReturn(new ValueRange().setValues(rows));

        ChatMessage msg = chatRepository.findAllByDeviceId("device1").get(0);

        assertThat(msg.suggestedQuestion()).isNull();
    }

    @Test
    void findAllByDeviceId_suggestedQuestion_값있으면_그대로_반환() throws IOException {
        LocalDateTime now = LocalDateTime.now();
        List<List<Object>> rows = List.of(
                List.of("device1", "assistant", "답변", now.toString(), "다음 질문은?")
        );

        when(values.get(anyString(), anyString())).thenReturn(getRequest);
        when(getRequest.execute()).thenReturn(new ValueRange().setValues(rows));

        ChatMessage msg = chatRepository.findAllByDeviceId("device1").get(0);

        assertThat(msg.suggestedQuestion()).isEqualTo("다음 질문은?");
    }

    @Test
    void findAllByDeviceId_4컬럼_미만_행_스킵() throws IOException {
        List<List<Object>> rows = List.of(
                List.of("device1", "user")
        );

        when(values.get(anyString(), anyString())).thenReturn(getRequest);
        when(getRequest.execute()).thenReturn(new ValueRange().setValues(rows));

        assertThat(chatRepository.findAllByDeviceId("device1")).isEmpty();
    }

    @Test
    void findAllByDeviceId_여러_메시지_순서_보존() throws IOException {
        LocalDateTime now = LocalDateTime.now();
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("device1", "user", "첫번째", now.toString(), ""));
        rows.add(List.of("device1", "assistant", "두번째", now.toString(), "다음 질문"));
        rows.add(List.of("device1", "user", "세번째", now.toString(), ""));

        when(values.get(anyString(), anyString())).thenReturn(getRequest);
        when(getRequest.execute()).thenReturn(new ValueRange().setValues(rows));

        List<ChatMessage> result = chatRepository.findAllByDeviceId("device1");

        assertThat(result).hasSize(3);
        assertThat(result.get(0).content()).isEqualTo("첫번째");
        assertThat(result.get(1).content()).isEqualTo("두번째");
        assertThat(result.get(2).content()).isEqualTo("세번째");
    }

    @Test
    void findAllByDeviceId_sheets_예외시_RuntimeException_발생() throws IOException {
        when(values.get(anyString(), anyString())).thenReturn(getRequest);
        when(getRequest.execute()).thenThrow(new IOException("연결 실패"));

        assertThatThrownBy(() -> chatRepository.findAllByDeviceId("device1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Google Sheets 조회 실패");
    }

    // ──────────────────────────────
    // append
    // ──────────────────────────────

    @Test
    void append_정상_execute_호출됨() throws IOException {
        when(values.append(anyString(), anyString(), any())).thenReturn(appendRequest);
        when(appendRequest.setValueInputOption(anyString())).thenReturn(appendRequest);

        ChatMessage message = new ChatMessage("device1", "user", "안녕", LocalDateTime.now(), null);

        chatRepository.append(message);

        verify(appendRequest, times(1)).execute();
    }

    @Test
    void append_RAW_옵션으로_호출됨() throws IOException {
        when(values.append(anyString(), anyString(), any())).thenReturn(appendRequest);
        when(appendRequest.setValueInputOption("RAW")).thenReturn(appendRequest);

        ChatMessage message = new ChatMessage("device1", "user", "안녕", LocalDateTime.now(), null);

        chatRepository.append(message);

        verify(appendRequest).setValueInputOption("RAW");
    }

    @Test
    void append_null_suggestedQuestion_빈문자열로_저장() throws IOException {
        when(values.append(anyString(), anyString(), any())).thenReturn(appendRequest);
        when(appendRequest.setValueInputOption(anyString())).thenReturn(appendRequest);

        ChatMessage message = new ChatMessage("device1", "user", "안녕", LocalDateTime.now(), null);

        assertThatNoException().isThrownBy(() -> chatRepository.append(message));
    }

    @Test
    void append_sheets_예외시_RuntimeException_발생() throws IOException {
        when(values.append(anyString(), anyString(), any())).thenReturn(appendRequest);
        when(appendRequest.setValueInputOption(anyString())).thenReturn(appendRequest);
        when(appendRequest.execute()).thenThrow(new IOException("연결 실패"));

        ChatMessage message = new ChatMessage("device1", "user", "안녕", LocalDateTime.now(), null);

        assertThatThrownBy(() -> chatRepository.append(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Google Sheets append 실패");
    }
}

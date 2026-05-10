package com.tikitalka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "google.sheets")
public record GoogleSheetsProperties(
        String applicationName,
        String spreadsheetId,
        String credentialsPath,
        String range,
        String newsRange // 뉴스용 범위 추가
) {
}

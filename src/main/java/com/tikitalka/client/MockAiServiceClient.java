package com.tikitalka.client;

import com.tikitalka.dto.AiServiceRequest;
import com.tikitalka.dto.AiServiceResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "ai.service.mock", havingValue = "true", matchIfMissing = true)
public class MockAiServiceClient implements AiServiceClient {

    private static final List<String> SOCCER_KEYWORDS = List.of(
            "축구", "football", "soccer", "골", "선수", "경기", "팀", "리그", "월드컵", "챔피언스리그",
            "프리미어리그", "라리가", "분데스리가", "세리에", "클럽", "감독", "코치", "득점", "어시스트"
    );

    @Override
    public AiServiceResponse call(AiServiceRequest request) {
        String userMessage = request.userMessage().toLowerCase();
        boolean isSoccer = SOCCER_KEYWORDS.stream().anyMatch(userMessage::contains);

        if (isSoccer) {
            return new AiServiceResponse(
                    "SOCCER_DOMAIN",
                    "축구 정보",
                    "[Mock] 축구 관련 질문이시군요! AI 서비스 연동 전 임시 응답입니다.",
                    List.of("최근 챔피언스리그 결과는?", "이번 시즌 득점왕은?"),
                    List.of()
            );
        } else {
            return new AiServiceResponse(
                    "GENERAL",
                    null,
                    "[Mock] 저는 축구 전문 챗봇입니다. 축구 관련 질문을 해주세요!",
                    List.of(),
                    List.of()
            );
        }
    }
}

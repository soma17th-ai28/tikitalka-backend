# tikitalka-backend

Tikitalka 프로젝트의 백엔드 시스템입니다. Google Sheets를 데이터베이스로 사용하며, 축구 챗봇 대화 이력 관리 및 AI 서비스 연동, 크롤링된 축구 뉴스 데이터 관리를 담당합니다.

---

## 0. [AI] 서비스 빠른 시작

```bash
cp .env.example .env  # UPSTAGE_API_KEY, NEWSDATA_API_KEY 입력
pip install -r requirements.txt
python -m uvicorn main:app --host 0.0.0.0 --port 8000
```

---

## 1. [Frontend] 채팅 API 통합 가이드

프론트엔드에서 축구 챗봇 기능을 구현하기 위한 API 명세입니다.

### Base URL

```
http://localhost:8080
```

### A. 대화 이력 조회

채팅 화면 진입 시 **최초 1회** 호출합니다.

- **Endpoint**: `GET /api/chat/history/{deviceId}`

**Response 예시**:
```json
[
  {
    "role": "user",
    "content": "손흥민 포지션이 뭐야?",
    "timestamp": "2026-05-07T10:00:00"
  },
  {
    "role": "assistant",
    "content": "손흥민은 토트넘 홋스퍼의 윙어입니다...",
    "suggestedQuestion": "이번 시즌 성적은 어때?",
    "timestamp": "2026-05-07T10:00:03"
  }
]
```

> 신규 사용자(이력 없음)는 빈 배열 `[]` 반환

### B. 메시지 전송

- **Endpoint**: `POST /api/chat/message`
- **Content-Type**: `application/json`

**Request Body**:
```json
{
  "deviceId": "uuid-xxxx-xxxx",
  "message": "손흥민 최근 경기 어때?"
}
```

**Response 예시**:
```json
{
  "role": "assistant",
  "content": "손흥민은 지난 경기에서 멀티골을 기록하며...",
  "suggestedQuestion": "이번 시즌 총 골 수는?",
  "timestamp": "2026-05-07T10:01:05"
}
```

> `suggestedQuestion`은 nullable — null이면 필드 자체가 응답에서 제외됩니다.

### C. 응답 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `role` | String | `user` 또는 `assistant` |
| `content` | String | 메시지 본문 |
| `suggestedQuestion` | String \| (없음) | AI가 제안하는 다음 질문 (nullable) |
| `timestamp` | String | ISO 8601 형식 |

### D. deviceId 규칙

- 앱 최초 실행 시 UUID를 생성해 로컬 스토리지에 저장
- 이후 모든 채팅 요청에 포함 — 같은 deviceId = 같은 사용자의 대화

### E. 에러 응답

```json
{ "error": "메시지 내용" }
```

| 상태코드 | 발생 상황 |
|----------|----------|
| 400 | deviceId 또는 message 누락/빈 값 |
| 500 | 서버 내부 오류 |
| 502 | AI 서비스 오류 (LLM 호출 실패) |
| 504 | AI 서비스 응답 타임아웃 (최대 ~70초) |

---

## 2. [Frontend] 뉴스 API 통합 가이드

프론트엔드에서 뉴스 피드 및 상세 정보를 조회하기 위한 API 명세입니다.

### A. 뉴스 피드 조회 (Pagination & Filtering)
- **Endpoint**: `GET /api/news`
- **Query Parameters**:
  - `tag` (Optional): 특정 리그나 카테고리 태그 (예: `premier-league`, `bundesliga`)
  - `page` (Default: 0): 페이지 번호
  - `size` (Default: 10): 페이지당 항목 수
  - `sort` (Default: `LATEST`): 정렬 방식 (`LATEST` - 최신순, `HOT` - 화제성순)

**Response 예시 (`PageResponse`)**:
```json
{
  "content": [
    {
      "id": "554a308f-1cfa-413a-bd23-b18db652c59d",
      "title": "뉴스 제목",
      "summary": "뉴스 요약 내용",
      "tag": "bundesliga",
      "publishedAt": "2026-05-08T05:20:00",
      "hotnessScore": 0,
      "url": "기사 원문 링크",
      "source": "출처 (예: Yahoo Entertainment)"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

### B. 뉴스 상세 조회
- **Endpoint**: `GET /api/news/{id}`

**Response 예시**:
```json
{
  "id": "554a308f-1cfa-413a-bd23-b18db652c59d",
  "title": "뉴스 제목",
  "summary": "뉴스 요약",
  "tag": "bundesliga",
  "publishedAt": "2026-05-08T05:20:00",
  "hotnessScore": 0,
  "originalContent": "뉴스 전체 본문 내용 (스크레이핑된 full text)",
  "url": "기사 원문 링크",
  "source": "출처"
}
```

---

## 3. [AI] 챗봇 서비스 연동 가이드

Spring Boot 백엔드와 AI 팀 FastAPI 서버 간 연동 명세입니다.

### A. 엔드포인트

- **Endpoint**: `POST /chat`
- **Host**: `AI_SERVICE_URL` 환경변수로 설정 (기본값: `http://localhost:8000`)

### B. 요청 / 응답

**Request**:
```json
{
  "session_id": "uuid-xxxx-xxxx",
  "message": "손흥민 최근 경기 어때?"
}
```

**Response**:
```json
{
  "session_id": "uuid-xxxx-xxxx",
  "reply": "손흥민은 지난 경기에서...",
  "suggested_question": "이번 시즌 총 골 수는?"
}
```

> `suggested_question`만 nullable입니다.

### C. 에러 응답

| 상황 | HTTP | Body |
|------|------|------|
| LLM API 오류 | 502 | `{ "detail": "LLM API error: <status_code>" }` |
| LLM 요청 실패 | 502 | `{ "detail": "LLM request failed: ..." }` |

> 뉴스 API 실패는 에러로 처리하지 않고 200으로 응답합니다.

### D. 타임아웃 참고

| 경로 | 소요 시간 |
|------|----------|
| 일반 LLM 응답 | ~30초 |
| 뉴스 검색 후 LLM 응답 | 최대 ~70초 |

백엔드 클라이언트 타임아웃은 **90초**로 설정되어 있습니다.

### E. 연동 방법

1. `AI_SERVICE_URL` 환경변수에 AI 서버 주소 설정
2. `application.yaml`에서 `ai.service.mock: false` 변경
3. AI 서버가 `0.0.0.0:8000`으로 기동되어 있어야 함

---

## 4. [AI/Crawler] 뉴스 데이터 전송 가이드

AI 크롤러가 수집한 데이터를 백엔드로 전송하여 저장하기 위한 명세입니다.

### A. 뉴스 등록 API (Internal)
- **Endpoint**: `POST /internal/api/news`
- **Content-Type**: `application/json`

**Request Body (JSON)**:
| 필드명 | 타입 | 설명 | 크롤러 대응 변수 |
| :--- | :--- | :--- | :--- |
| `title` | String | 뉴스의 제목 | `article['title']` |
| `source` | String | 출처/언론사명 | `article['source']['name']` |
| `publishedAt` | String | 발행 시간 (ISO 8601) | `article['publishedAt']` |
| `description` | String | 뉴스 요약 (Summary) | `article['description']` |
| `full_text` | String | 전체 본문 내용 | Scraped text |
| `url` | String | 원문 링크 (중복 체크 기준) | `article['url']` |
| `tag` | String | 뉴스 카테고리 태그 | `tag` (단일 값) |

**Request 예시**:
```json
{
  "title": "Borussia Dortmund vs Frankfurt Match Preview",
  "source": "The Football Faithful",
  "publishedAt": "2026-05-08T05:20:00Z",
  "description": "Short summary...",
  "full_text": "Long article content...",
  "url": "https://example.com/news/123",
  "tag": "bundesliga"
}
```

### B. 연동 규칙
1. **중복 방지**: `url` 필드를 기준으로 중복을 체크합니다. 이미 존재하는 URL은 무시됩니다.
2. **태그**: 여러 태그 중 대표 태그 **하나(String)**만 전송합니다.
3. **날짜**: `YYYY-MM-DDTHH:mm:ssZ` (UTC) 형식을 준수해야 합니다.

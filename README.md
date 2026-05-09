# tikitalka-backend

Tikitalka 프로젝트의 백엔드 시스템입니다. Google Sheets를 데이터베이스로 사용하며, 크롤링된 축구 뉴스 데이터를 관리하고 제공합니다.

---

## 1. [Frontend] 뉴스 API 통합 가이드

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

## 2. [AI/Crawler] 뉴스 데이터 전송 가이드

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

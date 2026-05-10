# 통합 뉴스 서비스 설계서 (Integrated News Service Design)

## 1. 시스템 아키텍처: 2단계 전략적 파이프라인

1.  **Collector & Local Processor (수집 및 개별 가공)**:
    - `NewsAPI`와 `Jsoup`으로 기사를 수집합니다.
    - 수집 즉시 **개별 기사**를 Solar AI에게 보내 본문을 분석하고 **메타데이터(요약, 키워드, 태그)**를 추출하여 `RawNews`에 저장합니다.
2.  **Global Scorer (통합 화제성 계산)**:
    - 일정 주기(예: 1시간)마다 **오늘 발생한 모든 RawNews 메타데이터**를 모아서 Solar AI에게 전달합니다.
    - AI는 전체 뉴스를 비교 분석하여 **상대적 중요도, 중복 사건 병합, 최종 hotnessScore**를 산출합니다.
3.  **Storage (최종 저장)**:
    - AI가 정제한 최종 결과물을 `NewsFeed` 시트에 저장/업데이트합니다.

## 2. 주요 데이터 모델

- **RawNews (수집용)**: `url`, `title`, `source`, `publishedAt`, `summary(AI 추출)`, `tag(AI 추출)`, `full_content`, `is_processed`
- **NewsFeed (서비스용)**: `id`, `title`, `summary`, `tag`, `hotnessScore`, `publishedAt`, `url(대표)`, `sources(보도 매체 리스트)`

## 3. Global Scoring 및 중복 처리 전략

### AI 통합 분석 (Global Prompt)
- **Input**: 오늘 발생한 모든 뉴스의 [제목, 요약, 태그, 매체명] 리스트
- **AI 임무**:
    1. **내용 기반 그룹화**: 서로 다른 매체의 비슷한 기사들을 하나의 '사건'으로 묶음.
    2. **상대적 스코어링**:
        - 보도 매체 수(집중도)가 높을수록 가점.
        - 리그 가중치(EPL, UCL 등) 반영.
        - AI가 판단한 사건의 임팩트 반영.
    3. **최종 스코어 산출**: 1~100점 사이로 조정.

## 4. Hotness Score 세부 기준 (AI 프롬프트 포함용)
- **Base Score**: AI가 판단한 사건의 중요도 (0~50)
- **Coverage Bonus**: 보도 매체 1개당 +5 (최대 +30)
- **League Bonus**: EPL/UCL(+15), Laliga/K-League(+10), 기타(+5)
- **Recency Penalty**: 시간이 지남에 따라 자연 감쇠

## 5. 단계별 구현 계획
1. **Step 1**: `RawNews` 수집 및 Jsoup 본문 추출 로직 구현.
2. **Step 2**: 개별 뉴스 메타데이터(요약, 태그) 추출 로직 구현.
3. **Step 3**: 오늘치 메타데이터를 모아 AI에게 전달하고 최종 스코어링하는 **Global Scorer** 구현.
4. **Step 4**: 최종 결과를 `NewsFeed` 시트에 반영 및 중복 병합 로직 완성.


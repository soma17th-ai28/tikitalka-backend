import os
import re
import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, field_validator
from typing import Any, Optional, TypedDict
from dotenv import load_dotenv

load_dotenv()

app = FastAPI(title="Soccer AI Chatbot API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

MAX_TURNS = 10
UPSTAGE_API_KEY = os.getenv("UPSTAGE_API_KEY", "")
UPSTAGE_API_URL = "https://api.upstage.ai/v1/chat/completions"
UPSTAGE_MODEL = "solar-pro"
SERPER_API_KEY = os.getenv("SERPER_API_KEY", "")
SERPER_API_URL = "https://google.serper.dev/news"
NEWS_NEED_PATTERN = re.compile(r"\[NEED_NEWS:\s*(.+?)\]")

END_SIGNALS = {"끝", "종료", "bye", "goodbye", "그만", "닫기", "exit", "quit", "고마워", "감사", "thank"}

SYSTEM_PROMPT = (
    "You are a soccer-expert friend: enthusiastic, knowledgeable, and casual.\n\n"
    "IMPORTANT: Respond ONLY in one of these three exact formats. No extra text, no notes, no explanations.\n\n"
    "FORMAT A — when you need current news (recent match results, scores, transfers, injuries, award winners like Ballon d'Or, Golden Boot, FIFA Best Player, current standings, any fact that changes year to year):\n"
    "[NEED_NEWS: search keyword]\n\n"
    "FORMAT B — for soccer-related questions:\n"
    "REPLY: <your answer in at most 2 lines>\n"
    "SUGGEST: <one follow-up soccer question>\n\n"
    "FORMAT B end-of-conversation (when user says 끝/종료/bye/그만/exit/감사/고마워):\n"
    "REPLY: <one sentence closing remark only>\n"
    "SUGGEST: NONE\n\n"
    "FORMAT C — for non-soccer questions INCLUDING greetings (안녕/hi/hello), weather, food, and any other general topics:\n"
    "REPLY: <one sentence friendly response>\n"
    "SUGGEST: NONE\n\n"
    "Always respond in Korean regardless of the language of any news context. Do NOT use FORMAT A if news context is already provided."
)

NEWS_SYSTEM_PROMPT = (
    "You are a soccer-expert friend: enthusiastic, knowledgeable, and casual.\n\n"
    "News context has been provided below. IMPORTANT: prioritize this news data over your training knowledge.\n"
    "If news and training knowledge conflict, follow the news.\n"
    "CRITICAL: If the news context does NOT explicitly contain the answer, say '최신 뉴스에서 확인할 수 없었습니다' and do NOT guess, infer, or fabricate any names, facts, or details.\n"
    "When using news data, naturally include phrases like '최근 뉴스에 따르면'.\n"
    "Respond ONLY in this exact format (no extra text):\n"
    "REPLY: <your answer in at most 2 lines>\n"
    "SUGGEST: <one follow-up question>\n\n"
    "Always respond in Korean regardless of the language of any news context."
)

RECENCY_SIGNALS = {
    "최근", "요즘", "지금", "현재", "어제", "오늘", "이번 시즌", "이번주", "이번 주",
    "이번달", "이번 달", "최신", "방금", "결과", "스코어", "순위", "이적", "부상",
    "영입", "방출", "계약", "올 시즌", "이번년도",
    "수상", "받은", "탄 사람", "누가 받", "누구야", "가장 최근",
}
SOCCER_KEYWORD_MAP: dict[str, str] = {
    "챔피언스리그": "Champions League",
    "ucl": "Champions League",
    "유로파리그": "Europa League",
    "프리미어리그": "Premier League",
    "epl": "Premier League",
    "라리가": "La Liga",
    "분데스리가": "Bundesliga",
    "세리에": "Serie A",
    "월드컵": "World Cup",
    "유로": "Euro",
    "아시안컵": "Asian Cup",
    "우승": "winner",
    "이적": "transfer",
    "부상": "injury",
    "순위": "standings",
    "결과": "result",
    "스코어": "score",
    "발롱도르": "Ballon d'Or",
    "골든부트": "Golden Boot",
    "골든글러브": "Golden Glove",
    "피파 올해의 선수": "FIFA Best Player",
    "올해의 선수": "FIFA Best Player",
    "수상자": "award winner",
}


CONTEXT_KEYWORDS: list[tuple[set[str], str]] = [
    ({"순위", "1위", "2위", "3위", "standings", "table", "랭킹"}, "standings"),
    ({"득점왕", "top scorer", "골든부트", "골 순위", "득점 순위"}, "top scorer"),
    ({"결과", "스코어", "경기 결과", "match result", "score"}, "match result"),
    ({"이적", "영입", "transfer", "signing"}, "transfer news"),
    ({"부상", "injury"}, "injury update"),
    ({"발롱도르", "골든글러브", "올해의 선수", "수상", "award"}, "award winner 2025"),
    ({"8강", "4강", "결승", "quarter", "semi", "final"}, "2025 results"),
]

def extract_news_keyword_heuristic(message: str) -> Optional[str]:
    lower = message.lower()
    has_recency = any(signal in lower for signal in RECENCY_SIGNALS)

    matched_en: Optional[str] = None
    for ko, en in SOCCER_KEYWORD_MAP.items():
        if ko in lower:
            matched_en = en
            break
    if not matched_en:
        for en_term in ("Champions League", "Premier League", "La Liga", "Bundesliga",
                        "Serie A", "World Cup", "transfer", "injury"):
            if en_term.lower() in lower:
                matched_en = en_term
                break

    # 축구 키워드가 없으면 뉴스 검색 안 함 (비축구 질문 오트리거 방지)
    if not matched_en:
        return None

    # matched_en이 이미 수상 관련 키워드면 "award winner" 접미사 불필요
    award_terms = {"Ballon d'Or", "Golden Boot", "Golden Glove", "FIFA Best Player", "award winner"}
    if matched_en in award_terms:
        context_suffix = "2025"
    else:
        context_suffix = "2025"
        for signals, suffix in CONTEXT_KEYWORDS:
            if any(s in lower for s in signals):
                context_suffix = suffix
                break

    if has_recency:
        return f"{matched_en} {context_suffix}"
    return None


class Message(TypedDict):
    role: str
    content: str


# In-memory session store: session_id -> list of messages
sessions: dict[str, list[Message]] = {}


class ChatRequest(BaseModel):
    session_id: str
    message: str

    @field_validator("session_id", "message")
    @classmethod
    def not_empty(cls, v: str) -> str:
        if not v or not v.strip():
            raise ValueError("field must not be empty")
        return v


class ChatResponse(BaseModel):
    session_id: str
    reply: str
    suggested_question: Optional[str]


def get_history(session_id: str) -> list[Message]:
    return sessions.get(session_id, [])


def save_turn(session_id: str, user_message: str, assistant_reply: str) -> None:
    history = sessions.setdefault(session_id, [])
    history.append(Message(role="user", content=user_message))
    history.append(Message(role="assistant", content=assistant_reply))
    if len(history) > MAX_TURNS * 2:
        sessions[session_id] = history[-(MAX_TURNS * 2):]


def is_end_signal(message: str) -> bool:
    lower = message.lower().strip()
    return any(signal in lower for signal in END_SIGNALS)


def parse_llm_response(content: str, end_signal: bool) -> tuple[str, Optional[str]]:
    suggested_question: Optional[str] = None
    reply_parts: list[str] = []
    suggest_value: Optional[str] = None

    for line in content.strip().splitlines():
        stripped = line.strip()
        if stripped.startswith("REPLY:"):
            reply_parts.append(stripped[len("REPLY:"):].strip())
        elif stripped.startswith("SUGGEST:"):
            suggest_value = stripped[len("SUGGEST:"):].strip()

    if reply_parts:
        reply = "\n".join(reply_parts[:2])
    else:
        # Strip residual [NEED_NEWS:...] tags and internal directives; use cleaned content
        cleaned = NEWS_NEED_PATTERN.sub("", content).strip()
        # If only SUGGEST: NONE remains (end signal case), give a closing reply
        if suggest_value and suggest_value.upper() == "NONE" and not cleaned:
            cleaned = "대화해서 즐거웠어! 또 궁금한 거 생기면 언제든지 찾아와 ⚽"
        reply = cleaned or "잠깐, 제대로 된 답변을 못 드렸네요. 다시 질문해 주실 수 있나요?"

    if not end_signal and suggest_value and suggest_value.upper() != "NONE":
        suggested_question = suggest_value

    return reply, suggested_question


def extract_news_keyword(content: str) -> Optional[str]:
    match = NEWS_NEED_PATTERN.search(content)
    return match.group(1).strip() if match else None


async def fetch_news_serper(keyword: str) -> Optional[str]:
    if not SERPER_API_KEY:
        return None
    try:
        async with httpx.AsyncClient() as client:
            response = await client.post(
                SERPER_API_URL,
                headers={"X-API-KEY": SERPER_API_KEY, "Content-Type": "application/json"},
                json={"q": keyword, "num": 3},
                timeout=10.0,
            )
            response.raise_for_status()
            data: dict[str, Any] = response.json()
        articles: list[Any] = data.get("news", [])[:3]
        if not articles:
            return None
        lines: list[str] = [f"Latest news about '{keyword}':"]
        for i, article in enumerate(articles, 1):
            title: str = str(article.get("title", ""))
            snippet: str = str(article.get("snippet", ""))
            date: str = str(article.get("date", ""))
            lines.append(f"{i}. {title}")
            if snippet:
                lines.append(f"   {snippet}")
            if date:
                lines.append(f"   ({date})")
        if len(articles) < 3:
            lines.append("(기사가 적어 정확하지 않을 수 있습니다.)")
        return "\n".join(lines)
    except Exception:
        return None


async def fetch_news(keyword: str) -> Optional[str]:
    return await fetch_news_serper(keyword)


async def call_upstage(history: list[Message], user_message: str) -> tuple[str, Optional[str]]:
    end_signal = is_end_signal(user_message)

    # Heuristic pre-fetch: if clearly a current-soccer-info question, fetch news before LLM call
    heuristic_keyword = extract_news_keyword_heuristic(user_message)
    if heuristic_keyword:
        news_context = await fetch_news(heuristic_keyword)
        fallback_note = (
            f"[{heuristic_keyword}에 대한 최신 뉴스를 찾지 못했습니다. 알고 있는 정보를 바탕으로 답변하되, 최신 정보가 아닐 수 있음을 언급해주세요.]"
            if not news_context else None
        )
        extra_context = news_context or fallback_note or ""
        messages_with_news: list[dict[str, str]] = [{"role": "system", "content": NEWS_SYSTEM_PROMPT}]
        for msg in history:
            messages_with_news.append({"role": msg["role"], "content": msg["content"]})
        messages_with_news.append({"role": "system", "content": extra_context})
        messages_with_news.append({"role": "user", "content": user_message})
        async with httpx.AsyncClient() as client:
            resp = await client.post(
                UPSTAGE_API_URL,
                headers={"Authorization": f"Bearer {UPSTAGE_API_KEY}", "Content-Type": "application/json"},
                json={"model": UPSTAGE_MODEL, "messages": messages_with_news},
                timeout=30.0,
            )
            resp.raise_for_status()
            data: dict[str, Any] = resp.json()
        return parse_llm_response(data["choices"][0]["message"]["content"], end_signal)

    # Default flow: first LLM call, then check if FORMAT A news tag was emitted
    messages: list[dict[str, str]] = [{"role": "system", "content": SYSTEM_PROMPT}]
    for msg in history:
        messages.append({"role": msg["role"], "content": msg["content"]})
    messages.append({"role": "user", "content": user_message})

    async with httpx.AsyncClient() as client:
        response = await client.post(
            UPSTAGE_API_URL,
            headers={
                "Authorization": f"Bearer {UPSTAGE_API_KEY}",
                "Content-Type": "application/json",
            },
            json={"model": UPSTAGE_MODEL, "messages": messages},
            timeout=30.0,
        )
        response.raise_for_status()
        data = response.json()

    content: str = data["choices"][0]["message"]["content"]

    news_keyword = extract_news_keyword(content)
    if news_keyword:
        news_context = await fetch_news(news_keyword)
        fallback_note = (
            f"[{news_keyword}에 대한 최신 뉴스를 찾지 못했습니다. 알고 있는 정보를 바탕으로 답변해주세요.]"
            if not news_context else None
        )
        news_system = NEWS_SYSTEM_PROMPT
        extra_context = news_context or fallback_note or ""
        messages_with_news2: list[dict[str, str]] = [{"role": "system", "content": news_system}]
        for msg in history:
            messages_with_news2.append({"role": msg["role"], "content": msg["content"]})
        messages_with_news2.append({"role": "system", "content": extra_context})
        messages_with_news2.append({"role": "user", "content": user_message})
        async with httpx.AsyncClient() as client:
            response2 = await client.post(
                UPSTAGE_API_URL,
                headers={
                    "Authorization": f"Bearer {UPSTAGE_API_KEY}",
                    "Content-Type": "application/json",
                },
                json={"model": UPSTAGE_MODEL, "messages": messages_with_news2},
                timeout=30.0,
            )
            response2.raise_for_status()
            data2: dict[str, Any] = response2.json()
        content = data2["choices"][0]["message"]["content"]

    return parse_llm_response(content, end_signal)


@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest) -> ChatResponse:
    history = get_history(request.session_id)

    try:
        reply, suggested_question = await call_upstage(history, request.message)
    except httpx.HTTPStatusError as e:
        raise HTTPException(status_code=502, detail=f"LLM API error: {e.response.status_code}") from e
    except httpx.RequestError as e:
        raise HTTPException(status_code=502, detail=f"LLM request failed: {e}") from e

    save_turn(request.session_id, request.message, reply)

    return ChatResponse(
        session_id=request.session_id,
        reply=reply,
        suggested_question=suggested_question,
    )

import os
import re
import logging
import httpx
from datetime import datetime
from fastapi import FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, field_validator
from typing import Any, Optional, TypedDict
from dotenv import load_dotenv

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler("server.log", encoding="utf-8"),
    ],
)
logger = logging.getLogger(__name__)

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
SERPER_API_KEY = os.getenv("NEWSDATA_API_KEY", "")
SERPER_API_URL = "https://google.serper.dev/search"
NEWS_NEED_PATTERN = re.compile(r"\[NEED_NEWS:\s*(.+?)\]")

END_SIGNALS = {"끝", "종료", "bye", "goodbye", "그만", "닫기", "exit", "quit", "고마워", "감사", "thank"}

ROUTING_PROMPT = (
    "You are a routing assistant for a soccer chatbot. "
    "Decide whether the user's question benefits from fetching current news.\n\n"
    "DEFAULT to YES. Only answer NO when the question is CLEARLY one of these timeless categories:\n"
    "- Official rules / laws of the game (e.g. offside rule, red card criteria)\n"
    "- Pure tactics or formations with no specific team or player mentioned\n"
    "- Historical facts unambiguously before 2020\n"
    "- Abstract definitions or concepts (e.g. 'what is a hat-trick?')\n\n"
    "Answer YES for everything else, including: any player, team, league, manager, contract, "
    "squad info, form, injury, transfer, score, standing, award, stat, season, comparison, "
    "opinion about a real-world event, or any question where a fresher answer would help.\n"
    "When in doubt, answer YES.\n\n"
    "If news is needed, respond EXACTLY: YES: <English search keyword>\n"
    "If not needed, respond EXACTLY: NO\n"
    "No other text."
)

SYSTEM_PROMPT = (
    "You are a soccer-expert friend: enthusiastic, knowledgeable, and casual.\n\n"
    "IMPORTANT: Respond ONLY in one of these two exact formats. No extra text, no notes, no explanations.\n\n"
    "FORMAT B — for soccer-related questions:\n"
    "REPLY: <your answer in at most 2 lines>\n"
    "SUGGEST: <one follow-up soccer question>\n\n"
    "FORMAT B end-of-conversation (when user says 끝/종료/bye/그만/exit/감사/고마워):\n"
    "REPLY: <one sentence closing remark only>\n"
    "SUGGEST: NONE\n\n"
    "FORMAT C — for non-soccer questions INCLUDING greetings (안녕/hi/hello), weather, food, and any other general topics:\n"
    "REPLY: <one sentence friendly response>\n"
    "SUGGEST: NONE\n\n"
    "Always respond in Korean."
)

NEWS_SYSTEM_PROMPT = (
    "You are a soccer-expert friend: enthusiastic, knowledgeable, and casual.\n\n"
    "News context has been provided below. IMPORTANT: prioritize this news data over your training knowledge.\n"
    "If news and training knowledge conflict, follow the news.\n"
    "Use the news as your ONLY source. Answer based on what the news says, even if partial or inconclusive.\n"
    "ONLY say '최신 뉴스에서 확인할 수 없었습니다' when the news has absolutely NO relevant information. "
    "Do NOT append this phrase alongside a real answer.\n"
    "When using news data, naturally include phrases like '최근 뉴스에 따르면'.\n"
    "Respond ONLY in FORMAT A below (no extra text, exactly one REPLY line):\n\n"
    "FORMAT A — for questions answered with latest news data:\n"
    "REPLY: <your answer in at most 2 lines>\n"
    "SUGGEST: <one follow-up question>\n\n"
    "Always respond in Korean regardless of the language of any news context."
)


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
        results: list[Any] = data.get("organic", [])[:5]
        if not results:
            return None
        lines: list[str] = [f"Search results about '{keyword}':"]
        for i, result in enumerate(results, 1):
            title: str = str(result.get("title", ""))
            snippet: str = str(result.get("snippet", ""))
            date: str = str(result.get("date", ""))
            lines.append(f"{i}. {title}")
            if snippet:
                lines.append(f"   {snippet}")
            if date:
                lines.append(f"   ({date})")
        return "\n".join(lines)
    except httpx.HTTPStatusError as e:
        logger.error("Serper API HTTP error | status=%s | body=%s", e.response.status_code, e.response.text)
        return None
    except Exception as e:
        logger.error("Serper API request failed | error=%s", e)
        return None


async def fetch_news(keyword: str) -> Optional[str]:
    return await fetch_news_serper(keyword)


async def call_upstage(history: list[Message], user_message: str) -> tuple[str, Optional[str]]:
    end_signal = is_end_signal(user_message)
    current_date = datetime.now().strftime("%Y년 %m월 %d일")
    date_context = f"현재 날짜: {current_date}"

    # Step 1: 라우팅 - 뉴스 필요 여부 및 검색 키워드 결정
    routing_messages: list[dict[str, str]] = [
        {"role": "system", "content": date_context},
        {"role": "system", "content": ROUTING_PROMPT},
        {"role": "user", "content": user_message},
    ]
    async with httpx.AsyncClient() as client:
        r1 = await client.post(
            UPSTAGE_API_URL,
            headers={"Authorization": f"Bearer {UPSTAGE_API_KEY}", "Content-Type": "application/json"},
            json={"model": UPSTAGE_MODEL, "messages": routing_messages},
            timeout=30.0,
        )
        r1.raise_for_status()
    routing_result: str = r1.json()["choices"][0]["message"]["content"].strip()
    logger.info("Routing result: %s", routing_result)

    news_keyword: Optional[str] = None
    if routing_result.upper().startswith("YES:"):
        news_keyword = routing_result[4:].strip().splitlines()[0].strip()

    # Step 2a: 뉴스 필요 → 검색 후 뉴스 기반 답변
    if news_keyword:
        logger.info("News keyword: %s", news_keyword)
        news_context = await fetch_news(news_keyword)
        logger.info("News context: %s", news_context)
        extra_context = news_context or f"[{news_keyword}에 대한 최신 뉴스를 찾지 못했습니다. 알고 있는 정보를 바탕으로 답변해주세요.]"
        answer_messages: list[dict[str, str]] = [{"role": "system", "content": NEWS_SYSTEM_PROMPT}]
        for msg in history:
            answer_messages.append({"role": msg["role"], "content": msg["content"]})
        answer_messages.append({"role": "system", "content": extra_context})
        answer_messages.append({"role": "user", "content": user_message})
    else:
        # Step 2b: 뉴스 불필요 → 직접 답변
        answer_messages = [
            {"role": "system", "content": date_context},
            {"role": "system", "content": SYSTEM_PROMPT},
        ]
        for msg in history:
            answer_messages.append({"role": msg["role"], "content": msg["content"]})
        answer_messages.append({"role": "user", "content": user_message})

    async with httpx.AsyncClient() as client:
        r2 = await client.post(
            UPSTAGE_API_URL,
            headers={"Authorization": f"Bearer {UPSTAGE_API_KEY}", "Content-Type": "application/json"},
            json={"model": UPSTAGE_MODEL, "messages": answer_messages},
            timeout=30.0,
        )
        r2.raise_for_status()
    content: str = r2.json()["choices"][0]["message"]["content"]

    return parse_llm_response(content, end_signal)


@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest) -> ChatResponse:
    logger.info("Request  | session=%s | message=%s", request.session_id, request.message)
    history = get_history(request.session_id)

    try:
        reply, suggested_question = await call_upstage(history, request.message)
    except httpx.HTTPStatusError as e:
        logger.error("LLM API error | session=%s | status=%s", request.session_id, e.response.status_code)
        raise HTTPException(status_code=502, detail=f"LLM API error: {e.response.status_code}") from e
    except httpx.RequestError as e:
        logger.error("LLM request failed | session=%s | error=%s", request.session_id, e)
        raise HTTPException(status_code=502, detail=f"LLM request failed: {e}") from e

    save_turn(request.session_id, request.message, reply)
    logger.info("Response | session=%s | reply=%s | suggested=%s", request.session_id, reply, suggested_question)

    return ChatResponse(
        session_id=request.session_id,
        reply=reply,
        suggested_question=suggested_question,
    )

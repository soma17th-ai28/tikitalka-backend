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
NEWSDATA_API_KEY = os.getenv("NEWSDATA_API_KEY", "")
NEWSDATA_API_URL = "https://newsdata.io/api/1/news"
NEWS_NEED_PATTERN = re.compile(r"\[NEED_NEWS:\s*(.+?)\]")

END_SIGNALS = {"끝", "종료", "bye", "goodbye", "그만", "닫기", "exit", "quit", "고마워", "감사", "thank"}

SYSTEM_PROMPT = (
    "You are a soccer-expert friend: enthusiastic, knowledgeable, and casual.\n\n"
    "If you need current news to answer accurately (recent match results, transfers, injuries), "
    "respond with ONLY this line and nothing else:\n"
    "[NEED_NEWS: <concise search keyword>]\n\n"
    "Otherwise, respond ONLY in this exact format (no other text):\n"
    "REPLY: <answer in at most 2 lines>\n"
    "SUGGEST: <one follow-up question to keep the conversation going>\n\n"
    "If the user signals they want to end the conversation "
    "(끝, 종료, bye, goodbye, 그만, exit, quit, thanks, 감사), write:\n"
    "SUGGEST: NONE\n\n"
    "Match the user's language (Korean or English)."
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
    reply = content.strip()
    suggested_question: Optional[str] = None

    reply_parts: list[str] = []
    suggest_value: Optional[str] = None

    for line in content.strip().splitlines():
        if line.startswith("REPLY:"):
            reply_parts.append(line[len("REPLY:"):].strip())
        elif line.startswith("SUGGEST:"):
            suggest_value = line[len("SUGGEST:"):].strip()

    if reply_parts:
        reply = "\n".join(reply_parts[:2])

    if not end_signal and suggest_value and suggest_value.upper() != "NONE":
        suggested_question = suggest_value

    return reply, suggested_question


def extract_news_keyword(content: str) -> Optional[str]:
    match = NEWS_NEED_PATTERN.search(content)
    return match.group(1).strip() if match else None


async def fetch_news(keyword: str) -> Optional[str]:
    if not NEWSDATA_API_KEY:
        return None
    try:
        async with httpx.AsyncClient() as client:
            response = await client.get(
                NEWSDATA_API_URL,
                params={"apikey": NEWSDATA_API_KEY, "q": keyword, "language": "en,ko", "size": 3},
                timeout=10.0,
            )
            response.raise_for_status()
            data: dict[str, Any] = response.json()
        articles: list[Any] = data.get("results", [])[:3]
        if not articles:
            return None
        lines: list[str] = [f"Latest news about '{keyword}':"]
        for i, article in enumerate(articles, 1):
            title: str = str(article.get("title", ""))
            summary: str = str(article.get("description") or article.get("content") or "")
            if len(summary) > 200:
                summary = summary[:200] + "..."
            lines.append(f"{i}. {title}")
            if summary:
                lines.append(f"   {summary}")
        return "\n".join(lines)
    except Exception:
        return None


async def call_upstage(history: list[Message], user_message: str) -> tuple[str, Optional[str]]:
    messages: list[dict[str, str]] = [{"role": "system", "content": SYSTEM_PROMPT}]
    for msg in history:
        messages.append({"role": msg["role"], "content": msg["content"]})
    messages.append({"role": "user", "content": user_message})

    end_signal = is_end_signal(user_message)

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
        data: dict[str, Any] = response.json()

    content: str = data["choices"][0]["message"]["content"]

    news_keyword = extract_news_keyword(content)
    if news_keyword:
        news_context = await fetch_news(news_keyword)
        if news_context:
            messages_with_news: list[dict[str, str]] = [{"role": "system", "content": SYSTEM_PROMPT}]
            for msg in history:
                messages_with_news.append({"role": msg["role"], "content": msg["content"]})
            messages_with_news.append({
                "role": "system",
                "content": f"[Recent news context]\n{news_context}",
            })
            messages_with_news.append({"role": "user", "content": user_message})
            async with httpx.AsyncClient() as client:
                response2 = await client.post(
                    UPSTAGE_API_URL,
                    headers={
                        "Authorization": f"Bearer {UPSTAGE_API_KEY}",
                        "Content-Type": "application/json",
                    },
                    json={"model": UPSTAGE_MODEL, "messages": messages_with_news},
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

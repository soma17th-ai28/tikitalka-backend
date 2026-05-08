from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, field_validator
from typing import Optional, TypedDict

app = FastAPI(title="Soccer AI Chatbot API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

MAX_TURNS = 10


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
    # Keep only the last MAX_TURNS turns (each turn = 2 messages)
    if len(history) > MAX_TURNS * 2:
        sessions[session_id] = history[-(MAX_TURNS * 2):]


@app.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest) -> ChatResponse:
    history = get_history(request.session_id)
    # history is available for LLM context (wired up in US-003)
    _ = history

    reply = "안녕! 무엇이 궁금해?"
    suggested_question: Optional[str] = "어제 경기 결과가 궁금하지 않아?"

    save_turn(request.session_id, request.message, reply)

    return ChatResponse(
        session_id=request.session_id,
        reply=reply,
        suggested_question=suggested_question,
    )

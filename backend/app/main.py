from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .api.main import api_router
from .services.bot_service import UnifiedRAGChatBot
from .services.file_service import index_files
from .api.deps import set_bot

@asynccontextmanager
async def lifespan(app: FastAPI):
    # 1. 파일 인덱싱
    index_files()
    
    # 2. 봇 초기화
    try:
        print("[System] 챗봇 엔진 초기화 중...")
        bot = UnifiedRAGChatBot()
        set_bot(bot)
        print("[System] 챗봇 준비 완료")
    except Exception as e:
        print(f"[Critical Error] 챗봇 초기화 실패: {e}")
    
    yield

app = FastAPI(lifespan=lifespan)

# CORS 설정
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:8060", 
        "http://sfolm.iptime.org:8060/",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router, prefix="/api")

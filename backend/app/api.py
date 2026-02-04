import os
import sys
from typing import Optional
from urllib.parse import unquote, quote 
from fastapi import FastAPI, HTTPException, APIRouter
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from contextlib import asynccontextmanager

from app.config import DATASET_DIR
from app.history import history_db

# 챗봇 모듈 임포트 시도
try:
    from app.bot import UnifiedRAGChatBot
except ImportError as e:
    print(f"❌ [Critical Error] 모듈 임포트 실패: {e}")
    sys.exit(1)

# ==========================================
# 1. 파일 위치 지도 & 챗봇 인스턴스 (Global)
# ==========================================
file_path_map = {}
bot = None

def index_files():
    """DATASET_DIR 하위의 모든 파일을 찾아 매핑"""
    global file_path_map
    file_path_map = {}
    
    print(f"\n🔍 [Debug] 파일 인덱싱 시작...")
    if not os.path.exists(DATASET_DIR):
        print(f"❌ [Error] dataset 폴더를 찾을 수 없습니다: {DATASET_DIR}")
        return

    count = 0
    for root, dirs, files in os.walk(DATASET_DIR):
        for file in files:
            full_path = os.path.join(root, file)
            file_path_map[file] = full_path
            count += 1
            
    print(f"✅ [System] 파일 인덱싱 완료! (총 {count}개 파일 발견)")

# ==========================================
# 2. FastAPI 수명주기 (Lifecycle)
# ==========================================
@asynccontextmanager
async def lifespan(app: FastAPI):
    index_files()
    global bot
    try:
        print("🤖 [System] 챗봇 엔진 초기화 중...")
        bot = UnifiedRAGChatBot()
        print("✅ [System] 챗봇 준비 완료")
    except Exception as e:
        print(f"❌ [Critical Error] 챗봇 초기화 실패: {e}")
        bot = None
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

# ==========================================
# 3. 데이터 모델 (Pydantic)
# ==========================================
class ChatRequest(BaseModel):
    message: str
    # session_id가 없어도(None) 허용되도록 Optional 처리
    session_id: Optional[str] = None 

# ==========================================
# 4. API 라우터 설정
# ==========================================

router = APIRouter(prefix="/api")

# --- 세션 관리 API ---
@router.get("/sessions")
def get_sessions():
    """모든 대화 목록 조회"""
    return history_db.get_sessions()

@router.get("/sessions/{session_id}")
def get_session_messages(session_id: str):
    """특정 대화의 메시지 내역 조회"""
    return history_db.get_messages(session_id)

@router.delete("/sessions/{session_id}")
def delete_session(session_id: str):
    """특정 대화 삭제"""
    try:
        history_db.delete_session(session_id)
        return {"status": "success", "message": "Session deleted"}
    except Exception as e:
        print(f"Error deleting session: {e}")
        raise HTTPException(status_code=500, detail="Failed to delete session")

# --- 메인 채팅 API ---
@router.post("/chat")
def chat(req: ChatRequest):
    if bot is None:
        raise HTTPException(status_code=500, detail="챗봇이 초기화되지 않았습니다.")
    
    curr_session_id = req.session_id
    if not curr_session_id:
        curr_session_id = history_db.create_session()

    past_history = history_db.get_messages(curr_session_id)

    history_db.add_message(curr_session_id, "user", req.message)

    try:
        result = bot.chat(req.message, chat_history=past_history)
    except Exception as e:
        print(f"Bot Error: {e}")
        result = {"response": "죄송합니다. 오류가 발생했습니다.", "graph_data": {}, "sources": []}
    
    history_db.add_message(curr_session_id, "assistant", result["response"])

    return {
        "response": result["response"],
        "graph_data": result.get("graph_data", {}),
        "sources": result.get("sources", []),
        "session_id": curr_session_id 
    }

# --- 관리자/유틸리티 API ---
@router.post("/build")
def build_db(target: str = "all", limit: int = None):
    try:
        from app.database import DBBuilder
        builder = DBBuilder()
        
        if target in ["process", "all"]:
            builder.build_process_db(limit)
        if target in ["project", "all"]:
            builder.build_project_db(limit)
            
        index_files() # 파일맵 갱신
        if bot:
            bot.reload_db() # 봇 DB 리로드
        
        return {"status": "success", "message": f"DB Build Complete ({target})"}
    except Exception as e:
        print(f"❌ [Error] DB 빌드 중 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/download/{filename}")
def download_file(filename: str):
    decoded_filename = unquote(filename)
    file_path = file_path_map.get(decoded_filename)
    
    if not file_path or not os.path.exists(file_path):
        index_files() # 없으면 재검색
        file_path = file_path_map.get(decoded_filename)

    if file_path and os.path.exists(file_path):
        encoded_filename = quote(decoded_filename)
        return FileResponse(
            path=file_path,
            filename=decoded_filename, 
            media_type='application/octet-stream',
            headers={
                "Content-Disposition": f"attachment; filename*=UTF-8''{encoded_filename}"
            }
        )
    
    raise HTTPException(status_code=404, detail="파일을 찾을 수 없습니다.")


app.include_router(router)
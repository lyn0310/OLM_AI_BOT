from fastapi import APIRouter, HTTPException
from ...services.history_service import history_db

router = APIRouter()

@router.get("")
def get_sessions():
    """모든 대화 목록 조회"""
    return history_db.get_sessions()

@router.get("/{session_id}")
def get_session_messages(session_id: str):
    """특정 대화의 메시지 내역 조회"""
    return history_db.get_messages(session_id)

@router.delete("/{session_id}")
def delete_session(session_id: str):
    """특정 대화 삭제"""
    try:
        history_db.delete_session(session_id)
        return {"status": "success", "message": "Session deleted"}
    except Exception as e:
        print(f"Error deleting session: {e}")
        raise HTTPException(status_code=500, detail="Failed to delete session")

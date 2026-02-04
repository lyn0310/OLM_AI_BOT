from fastapi import APIRouter, HTTPException
from ...schemas.chat import ChatRequest
from ...services.history_service import history_db
from ..deps import get_bot

router = APIRouter()

@router.post("")
def chat(req: ChatRequest):
    bot = get_bot()
    
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

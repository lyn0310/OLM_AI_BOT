from typing import Optional
from ..services.bot_service import UnifiedRAGChatBot

_bot_instance: Optional[UnifiedRAGChatBot] = None

def get_bot() -> UnifiedRAGChatBot:
    if _bot_instance is None:
        raise ValueError("챗봇이 초기화되지 않았습니다.")
    return _bot_instance

def set_bot(bot: UnifiedRAGChatBot):
    global _bot_instance
    _bot_instance = bot

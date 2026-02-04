import sqlite3
import uuid
from datetime import datetime
import os
from app.config import DATABASE_DIR

class ChatHistoryManager:
    def __init__(self):
        # DB 파일 경로
        self.db_path = os.path.join(DATABASE_DIR, "chat_history.db")
        self._init_db()

    def _init_db(self):
        with sqlite3.connect(self.db_path) as conn:
            cursor = conn.cursor()
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    id TEXT PRIMARY KEY,
                    title TEXT,
                    created_at TIMESTAMP
                )
            """)
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT,
                    role TEXT,
                    content TEXT,
                    created_at TIMESTAMP,
                    FOREIGN KEY(session_id) REFERENCES sessions(id)
                )
            """)
            conn.commit()

    def create_session(self, title="새로운 대화"):
        session_id = str(uuid.uuid4())
        created_at = datetime.now()
        with sqlite3.connect(self.db_path) as conn:
            conn.execute(
                "INSERT INTO sessions (id, title, created_at) VALUES (?, ?, ?)",
                (session_id, title, created_at)
            )
        return session_id

    def add_message(self, session_id, role, content):
        created_at = datetime.now()
        with sqlite3.connect(self.db_path) as conn:
            conn.execute(
                "INSERT INTO messages (session_id, role, content, created_at) VALUES (?, ?, ?, ?)",
                (session_id, role, content, created_at)
            )
            # 첫 질문일 경우 제목 업데이트
            if role == "user":
                cursor = conn.cursor()
                cursor.execute("SELECT count(*) FROM messages WHERE session_id = ?", (session_id,))
                count = cursor.fetchone()[0]
                if count <= 2: 
                    short_title = content[:30] + "..." if len(content) > 30 else content
                    conn.execute("UPDATE sessions SET title = ? WHERE id = ?", (short_title, session_id))

    def get_sessions(self):
        with sqlite3.connect(self.db_path) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.execute("SELECT * FROM sessions ORDER BY created_at DESC")
            return [dict(row) for row in cursor.fetchall()]

    def get_messages(self, session_id):
        with sqlite3.connect(self.db_path) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.execute(
                "SELECT role, content FROM messages WHERE session_id = ? ORDER BY id ASC", 
                (session_id,)
            )
            return [dict(row) for row in cursor.fetchall()]

    def delete_session(self, session_id):
        with sqlite3.connect(self.db_path) as conn:
            conn.execute("DELETE FROM messages WHERE session_id = ?", (session_id,))
            conn.execute("DELETE FROM sessions WHERE id = ?", (session_id,))

history_db = ChatHistoryManager()
import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import './css/ChatSidebar.css';

import API_BASE_URL from '../../../config';

const ChatSidebar = ({ sessions, currentSessionId, onSelectSession, onNewChat, onSessionsUpdate, theme, toggleTheme }) => {
    const [openMenuId, setOpenMenuId] = useState(null);
    const menuRef = useRef(null);

    useEffect(() => {
        const handleClickOutside = (event) => { if (menuRef.current && !menuRef.current.contains(event.target)) setOpenMenuId(null); };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const handleDelete = async (e, sessionId) => {
        e.stopPropagation();
        if (window.confirm("삭제하시겠습니까?")) {
            try { await axios.delete(`${API_BASE_URL}/sessions/${sessionId}`); if (onSessionsUpdate) onSessionsUpdate(); setOpenMenuId(null); } 
            catch (error) { alert("오류 발생"); }
        }
    };
    const toggleMenu = (e, sessionId) => { e.stopPropagation(); setOpenMenuId(openMenuId === sessionId ? null : sessionId); };

    return (
        <div className="chat-sidebar">
            {/* 상단: 새 채팅 버튼 */}
            <button className="new-chat-btn" onClick={onNewChat}>+ 새로운 채팅</button>
            
            <div className="session-list-header">최근 대화</div>
            
            {/* 목록 영역 */}
            <div className="session-list">
                {!sessions || sessions.length === 0 ? <div className="empty-session">대화 기록 없음</div> : 
                    sessions.map(session => (
                        <div key={session.id} className={`session-item ${session.id === currentSessionId ? 'active' : ''}`} onClick={() => onSelectSession(session.id)}>
                            <span className="session-icon">💬</span>
                            <span className="session-title">{session.title || "새로운 대화"}</span>
                            <div className="session-menu-wrapper">
                                <button className="menu-trigger-btn" onClick={(e) => toggleMenu(e, session.id)}>⋮</button>
                                {openMenuId === session.id && (
                                    <div className="context-menu" ref={menuRef}>
                                        <div className="menu-option" onClick={(e) => handleDelete(e, session.id)}>🗑️ 삭제</div>
                                    </div>
                                )}
                            </div>
                        </div>
                    ))
                }
            </div>

            {/* 🔥 하단: 테마 토글 스위치 */}
            <div className="sidebar-footer">
                <div className="theme-toggle-wrapper" onClick={toggleTheme}>
                    <div className={`theme-toggle-track ${theme === 'dark' ? 'dark' : 'light'}`}>
                        <div className="theme-toggle-thumb">
                        </div>
                    </div>
                    <span className="theme-label">{theme === 'dark' ? '다크 모드' : '라이트 모드'}</span>
                </div>
            </div>
        </div>
    );
};
export default ChatSidebar;
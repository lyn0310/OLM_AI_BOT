import { useState, useRef, useEffect } from 'react';
import axios from 'axios';
import './Chatbot.css';

import ChatHeader from './components/ChatHeader';
import MessageList from './components/MessageList';
import ChatInput from './components/ChatInput';
import ChatLauncher from './components/ChatLauncher';
import GraphVisualizer from './components/GraphVisualizer';
import ChatSidebar from './components/ChatSidebar';
import ProcessPopup from './components/ProcessPopup';
import API_BASE_URL from '../../config';

const ChatbotContainer = () => {
    const DEFAULT_W = 380;
    const DEFAULT_H = 600;

    const [isOpen, setIsOpen] = useState(false);
    const [isDragging, setIsDragging] = useState(false);
    const [isResizing, setIsResizing] = useState(false);

    const [windowState, setWindowState] = useState({
        x: window.innerWidth - (DEFAULT_W + 20),
        y: window.innerHeight - (DEFAULT_H + 50),
        width: DEFAULT_W,
        height: DEFAULT_H
    });

    const [isMaximized, setIsMaximized] = useState(false);
    const prevWindowState = useRef(null);
    const requestRef = useRef();

    const [isSidebarOpen, setIsSidebarOpen] = useState(false);
    const [messages, setMessages] = useState([{ role: 'assistant', content: '안녕하세요! OLM 챗봇입니다.' }]);
    const [input, setInput] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [isDevMode, setIsDevMode] = useState(false);
    const [theme, setTheme] = useState('dark');

    const [graphData, setGraphData] = useState({ nodes: [], links: [] });
    const [sessionId, setSessionId] = useState(null);
    const [sessions, setSessions] = useState([]);

    const containerRef = useRef(null);
    const dragRef = useRef({ isDragging: false, startX: 0, startY: 0, initialLeft: 0, initialTop: 0 });
    const resizeRef = useRef({ isResizing: false, direction: '', startX: 0, startY: 0, initialW: 0, initialH: 0, initialX: 0, initialY: 0 });

    const [popupData, setPopupData] = useState(null);

    const fetchSessions = async () => {
        try {
            const res = await axios.get(`${API_BASE_URL}/sessions`);
            if (Array.isArray(res.data)) {
                setSessions(res.data);
            } else {
                console.warn("Invalid sessions data format:", res.data);
                setSessions([]);
            }
        } catch (e) {
            console.error("Failed to fetch sessions:", e);
            setSessions([]);
        }
    };

    useEffect(() => { if (isOpen) fetchSessions(); }, [isOpen]);

    const handleSelectSession = async (id) => {
        setSessionId(id);
        setIsLoading(true);
        try {
            const res = await axios.get(`${API_BASE_URL}/sessions/${id}`);
            if (Array.isArray(res.data)) {
                setMessages(res.data);
            } else {
                console.warn("Invalid messages format:", res.data);
                setMessages([]);
            }
            setGraphData({ nodes: [], links: [] });
        } catch (e) {
            console.error("Failed to load session:", e);
            setMessages([]);
        } finally { setIsLoading(false); }
    };

    const handleNewChat = () => {
        setSessionId(null);
        setMessages([{ role: 'assistant', content: '안녕하세요! OLM 챗봇입니다.' }]);
        setGraphData({ nodes: [], links: [] });
    };

    const sendMessage = async () => {
        if (!input.trim()) return;
        const currentInput = input;
        setInput('');
        setMessages(prev => [...prev, { role: 'user', content: currentInput }]);
        setIsLoading(true);
        try {
            const res = await axios.post(`${API_BASE_URL}/chat`, { message: currentInput, session_id: sessionId || null });
            setMessages(prev => [...prev, { role: 'assistant', content: res.data.response }]);
            if (res.data.graph_data?.nodes?.length > 0) {
                setGraphData(res.data.graph_data);
                if (!isDevMode) setIsDevMode(true);
            }
            if (!sessionId && res.data.session_id) {
                setSessionId(res.data.session_id);
                fetchSessions();
            } else if (sessionId) fetchSessions();
        } catch (error) {
            setMessages(prev => [...prev, { role: 'assistant', content: "오류 발생" }]);
        } finally { setIsLoading(false); }
    };

    const handleToggleMaximize = () => {
        if (isDevMode) {
            setIsDevMode(false);
        }

        if (isMaximized) {
            setWindowState({
                x: window.innerWidth - (DEFAULT_W + 20),
                y: window.innerHeight - (DEFAULT_H + 50),
                width: DEFAULT_W,
                height: DEFAULT_H
            });
            setIsSidebarOpen(false);
            setIsMaximized(false);
        } else {
            prevWindowState.current = { ...windowState };
            const targetWidth = window.innerWidth * (2 / 3);
            const targetHeight = window.innerHeight * (2 / 3);
            const targetX = (window.innerWidth - targetWidth) / 2;
            const targetY = (window.innerHeight - targetHeight) / 2;
            setWindowState({ x: targetX, y: targetY, width: targetWidth, height: targetHeight });
            if (!isSidebarOpen) setIsSidebarOpen(true);
            setIsMaximized(true);
        }
    };

    useEffect(() => {
        const handleMouseMove = (e) => {
            if (dragRef.current.isDragging) {
                if (requestRef.current) return;
                requestRef.current = requestAnimationFrame(() => {
                    const dx = e.clientX - dragRef.current.startX;
                    const dy = e.clientY - dragRef.current.startY;
                    setWindowState(prev => {
                        let newX = dragRef.current.initialLeft + dx;
                        let newY = dragRef.current.initialTop + dy;
                        const maxX = window.innerWidth - prev.width;
                        const maxY = window.innerHeight - prev.height;
                        return { ...prev, x: Math.max(0, Math.min(newX, maxX)), y: Math.max(0, Math.min(newY, maxY)) };
                    });
                    requestRef.current = null;
                });
            }
            if (resizeRef.current.isResizing) {
                if (requestRef.current) return;
                requestRef.current = requestAnimationFrame(() => {
                    const dx = e.clientX - resizeRef.current.startX;
                    const dy = e.clientY - resizeRef.current.startY;
                    const { direction, initialW, initialH, initialX, initialY } = resizeRef.current;
                    let newW = initialW; let newH = initialH; let newX = initialX; let newY = initialY;
                    if (direction.includes('r')) newW = Math.max(DEFAULT_W, initialW + dx);
                    if (direction.includes('l')) {
                        const rightEdge = initialX + initialW;
                        let w = Math.max(DEFAULT_W, initialW - dx);
                        newW = w; newX = rightEdge - newW;
                    }
                    if (direction.includes('b')) newH = Math.max(400, initialH + dy);
                    if (direction.includes('t')) {
                        const bottomEdge = initialY + initialH;
                        let h = Math.max(400, initialH - dy);
                        newH = h; newY = bottomEdge - newH;
                    }
                    if (newX + newW > window.innerWidth) newW = window.innerWidth - newX;
                    if (newY + newH > window.innerHeight) newH = window.innerHeight - newY;
                    if (newX < 0) { newW = initialX + initialW; newX = 0; }
                    if (newY < 0) { newH = initialY + initialH; newY = 0; }
                    setWindowState(prev => ({ ...prev, x: newX, y: newY, width: newW, height: newH }));
                    requestRef.current = null;
                });
            }
        };
        const handleMouseUp = () => {
            dragRef.current.isDragging = false;
            resizeRef.current.isResizing = false;
            setIsDragging(false);
            setIsResizing(false);
            if (requestRef.current) {
                cancelAnimationFrame(requestRef.current);
                requestRef.current = null;
            }
        };
        if (isOpen) {
            window.addEventListener('mousemove', handleMouseMove);
            window.addEventListener('mouseup', handleMouseUp);
        }
        return () => {
            window.removeEventListener('mousemove', handleMouseMove);
            window.removeEventListener('mouseup', handleMouseUp);
        };
    }, [isOpen]);

    const startDrag = (e) => {
        if (!isOpen) return;
        setIsDragging(true);
        dragRef.current = {
            isDragging: true,
            startX: e.clientX,
            startY: e.clientY,
            initialLeft: windowState.x,
            initialTop: windowState.y
        };
    };

    const startResize = (e, direction) => {
        e.stopPropagation(); e.preventDefault();
        setIsResizing(true);
        resizeRef.current = {
            isResizing: true, direction, startX: e.clientX, startY: e.clientY,
            initialW: windowState.width, initialH: windowState.height,
            initialX: windowState.x, initialY: windowState.y
        };
    };

    const handleNodeClick = (node, event) => {
        if (node.content) {
            setPopupData({
                content: node.content,
                position: { x: event.clientX, y: event.clientY }
            });
        } else {
            setPopupData(null);
        }
    };

    const handleBackgroundClick = () => {
        if (popupData) setPopupData(null);
    };

    return (
        <>
            {(isDragging || isResizing) && <div className="interaction-overlay" />}
            <div
                className={`chat-window ${isOpen ? 'open' : 'closed'} ${isDevMode ? 'dev-mode' : ''} ${(isDragging || isResizing) ? 'dragging' : ''}`}
                style={{
                    left: isOpen ? `${windowState.x}px` : 'auto',
                    top: isOpen ? `${windowState.y}px` : 'auto',
                    width: `${windowState.width}px`,
                    height: `${windowState.height}px`,
                    position: 'fixed'
                }}
                ref={containerRef}
                data-theme={theme}
                onClick={handleBackgroundClick}
            >
                {/* 헤더 영역 클릭 시 드래그 시작 */}
                <div onMouseDown={startDrag}>
                    <ChatHeader
                        onClose={() => setIsOpen(false)}
                        onClear={handleNewChat}
                        isDevMode={isDevMode}
                        onDevModeToggle={() => {
                            console.log("DEV 모드 토글 실행");
                            setIsDevMode(!isDevMode);
                        }}
                        isSidebarOpen={isSidebarOpen}
                        onToggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)}
                        isMaximized={isMaximized}
                        onToggleMaximize={handleToggleMaximize}
                    />
                </div>

                <div className="chat-body-wrapper">
                    <div className={`sidebar-wrapper ${isSidebarOpen ? 'expanded' : 'collapsed'}`}>
                        <ChatSidebar
                            sessions={sessions} currentSessionId={sessionId} onSelectSession={handleSelectSession} onNewChat={handleNewChat} onSessionsUpdate={fetchSessions}
                            theme={theme} toggleTheme={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
                        />
                    </div>
                    <div className="chat-body-container">
                        <div className="chat-section">
                            <MessageList messages={messages} isLoading={isLoading} />
                            <ChatInput input={input} setInput={setInput} onSendMessage={sendMessage} isLoading={isLoading} />
                        </div>

                        {isDevMode && (
                            <div className="graph-section">
                                <GraphVisualizer
                                    graphData={graphData}
                                    theme={theme}
                                    onNodeClick={handleNodeClick}
                                />
                            </div>
                        )}
                    </div>
                </div>
                {['t', 'r', 'b', 'l', 'tl', 'tr', 'bl', 'br'].map(dir => (
                    <div key={dir} className={`resizer ${dir}`} onMouseDown={(e) => startResize(e, dir)} />
                ))}
            </div>

            {popupData && (
                <ProcessPopup
                    content={popupData.content}
                    position={popupData.position}
                    onClose={() => setPopupData(null)}
                    theme={theme}
                />
            )}

            {!isOpen && <div className="widget-launcher-pos" onClick={() => setIsOpen(true)}><ChatLauncher isOpen={isOpen} toggleChat={() => { }} /></div>}
        </>
    );
};

export default ChatbotContainer;
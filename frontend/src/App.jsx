import { useState, useRef, useEffect } from 'react';
import axios from 'axios';
import './App.css';

import API_BASE_URL from './config';

function App() {
  const [isOpen, setIsOpen] = useState(false);
  const [isDevMode, setIsDevMode] = useState(false);

  const [messages, setMessages] = useState([
    { 
      role: 'assistant', 
      content: '안녕하세요! OLM 챗봇입니다. 궁금한 점을 물어보세요.',
      debugInfo: null 
    }
  ]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const messagesEndRef = useRef(null);

  const toggleChat = () => setIsOpen(!isOpen);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    if (isOpen) scrollToBottom();
  }, [messages, isOpen, isDevMode]); // 모드 바뀌어도 스크롤 조정

  const sendMessage = async () => {
    if (!input.trim()) return;

    const userMessage = { role: 'user', content: input };
    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setIsLoading(true);

    try {
        const response = await axios.post(`${API_BASE_URL}/chat`, {
        message: input
      });

      const data = response.data;
      
      const botMessage = { 
        role: 'assistant', 
        content: data.response,
        debugInfo: {
            sources: data.sources || [],  
            query: data.search_query || "N/A", 
            thoughts: "사용자의 질문을 분석하여 관련 문서를 검색했습니다." 
        }
      };
      setMessages((prev) => [...prev, botMessage]);
    } catch (error) {
      console.error("Error:", error);
      setMessages((prev) => [...prev, { role: 'assistant', content: "죄송합니다. 오류가 발생했습니다." }]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyPress = (e) => {
    if (e.key === 'Enter') sendMessage();
  };

  return (
    <div className={`widget-container ${isDevMode ? 'dev-layout' : ''}`}>
      
      <div className={`chat-window ${isOpen ? 'open' : 'closed'}`}>
        
        {/* 헤더 */}
        <div className="chat-header">
          <div className="header-title">
            <span>OLM ChatBot</span>
            {/* 개발 모드 토글 스위치 */}
            <label className="switch" title="개발자 모드 켜기/끄기">
                <input 
                    type="checkbox" 
                    checked={isDevMode} 
                    onChange={() => setIsDevMode(!isDevMode)} 
                />
                <span className="slider round"></span>
            </label>
          </div>
          <button className="close-btn" onClick={toggleChat}>✕</button>
        </div>

        {/* 메시지 영역 */}
        <div className="messages-area">
          {messages.map((msg, index) => (
            <div key={index} className={`message-row ${msg.role}`}>
                
                {/* 1. 기본 말풍선 */}
                <div className={`message ${msg.role}`}>
                    <div className="bubble">
                        {msg.content.split('\n').map((line, i) => (
                        <span key={i}>{line}<br /></span>
                        ))}
                    </div>
                </div>

                {/* 2. [개발 모드] 추가 정보 패널 */}
                {isDevMode && msg.role === 'assistant' && msg.debugInfo && (
                    <div className="debug-panel">
                        <div className="debug-item">
                            <span className="debug-label">🔍 검색 쿼리:</span>
                            <span className="debug-val">{msg.debugInfo.query}</span>
                        </div>
                        <div className="debug-item">
                            <span className="debug-label">📚 참고 문서:</span>
                            <ul className="source-list">
                                {msg.debugInfo.sources.length > 0 ? (
                                    msg.debugInfo.sources.map((src, i) => <li key={i}>{src}</li>)
                                ) : (<li>참고 문서 없음</li>)}
                            </ul>
                        </div>
                        <div className="debug-item">
                              <span className="debug-label">🤖 AI 생각:</span>
                              <p className="debug-val">{msg.debugInfo.thoughts}</p>
                        </div>
                    </div>
                )}
            </div>
          ))}
          {isLoading && <div className="message assistant"><div className="bubble">...</div></div>}
          <div ref={messagesEndRef} />
        </div>

        {/* 입력창 */}
        <div className="input-area">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyPress={handleKeyPress}
            placeholder="질문을 입력하세요..."
            disabled={isLoading}
          />
          <button onClick={sendMessage} disabled={isLoading}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <line x1="22" y1="2" x2="11" y2="13"></line>
              <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
            </svg>
          </button>
        </div>
      </div>

      {/* 런처 버튼 */}
      <button className="chat-launcher" onClick={toggleChat}>
        {isOpen ? (
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        ) : (
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path>
          </svg>
        )}
      </button>
    </div>
  );
}

export default App;
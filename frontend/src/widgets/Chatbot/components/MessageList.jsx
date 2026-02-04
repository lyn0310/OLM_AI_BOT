import { useEffect, useRef } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import '../components/css/MessageList.css';

const MessageItem = ({ msg }) => {
    // 1. 사용자(User) 메시지 처리
    // 사용자는 보통 텍스트만 입력하므로, 줄바꿈만 처리하고 단순 텍스트로 보여줌
    if (msg.role === 'user') {
        return (
            <div className={`message ${msg.role}`}>
                <div className="bubble">
                    {msg.content.split('\n').map((line, i) => (
                        <span key={i}>
                            {line}
                            <br />
                        </span>
                    ))}
                </div>
            </div>
        );
    }

    // 2. 어시스턴트(Assistant) 메시지 처리
    return (
        <div className={`message ${msg.role}`}>
            <div className="bubble">
                {/* markdown-body 클래스를 붙여야 아까 작성한 CSS(표 스타일 등)가 적용됩니다 */}
                <div className="markdown-body">
                    <ReactMarkdown 
                        remarkPlugins={[remarkGfm]} // 표(Table) 지원 플러그인
                        components={{
                            // a 태그(링크) 커스텀: 클릭 시 새 탭에서 열기
                            a: ({node, ...props}) => (
                                <a {...props} target="_blank" rel="noopener noreferrer" />
                            )
                        }}
                    >
                        {msg.content}
                    </ReactMarkdown>
                </div>
            </div>
        </div>
    );
};

const MessageList = ({ messages, isLoading }) => {
    const messagesEndRef = useRef(null);

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
    }, [messages, isLoading]);

    return (
        <div className="messages-area">
            {messages.map((msg, index) => (
                <MessageItem key={index} msg={msg} />
            ))}

            {isLoading && (
                <div className="message assistant">
                    <div className="typing-indicator">
                        <span></span>
                        <span></span>
                        <span></span>
                    </div>
                </div>
            )}
            
            <div ref={messagesEndRef} />
        </div>
    );
};

export default MessageList;
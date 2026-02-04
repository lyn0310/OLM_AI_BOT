const ChatInput = ({ input, setInput, onSendMessage, isLoading }) => {
    const handleKeyPress = (e) => {
        if (e.key === 'Enter') onSendMessage();
    };

    return (
        <div className="input-area">
            <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyPress={handleKeyPress}
                placeholder="질문을 입력하세요..."
                disabled={isLoading}
            />
            <button onClick={onSendMessage} disabled={isLoading}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                    <line x1="22" y1="2" x2="11" y2="13"></line>
                    <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
                </svg>
            </button>
        </div>
    );
};
export default ChatInput;
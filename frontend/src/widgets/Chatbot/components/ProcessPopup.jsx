import React, { useState, useRef, useLayoutEffect, useEffect } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import '../components/css/ProcessPopup.css'; 

const Icons = {
    FileText: () => (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/>
        </svg>
    ),
    Copy: () => (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
        </svg>
    ),
    Check: () => (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <polyline points="20 6 9 17 4 12"/>
        </svg>
    ),
    X: () => (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
        </svg>
    )
};

// 메모이제이션된 마크다운 뷰어
const MarkdownViewer = React.memo(({ content, theme }) => {
    // 라이트 모드일 때 글자색 조정
    const headerColor = theme === 'light' ? '#333' : '#fff';
    const textColor = theme === 'light' ? '#444' : '#e0e0e0';
    const strongColor = theme === 'light' ? '#d97706' : '#ffd700'; // 라이트: 오렌지, 다크: 골드
    const borderColor = theme === 'light' ? '#ddd' : '#444';

    return (
        <div className="popup-body markdown-body" onMouseDown={e => e.stopPropagation()}>
            <ReactMarkdown 
                remarkPlugins={[remarkGfm]}
                components={{
                    a: ({node, ...props}) => (
                        <a {...props} target="_blank" rel="noopener noreferrer" style={{ color: '#667eea', textDecoration: 'underline', cursor: 'pointer' }} onClick={e => e.stopPropagation()} />
                    ),
                    p: ({node, ...props}) => <p style={{ color: textColor, margin: '8px 0', lineHeight: '1.6' }} {...props} />,
                    li: ({node, ...props}) => <li style={{ color: textColor, marginBottom: '4px' }} {...props} />,
                    h1: ({node, ...props}) => <h1 style={{ color: headerColor, fontSize: '1.4em', borderBottom: `1px solid ${borderColor}`, paddingBottom: '0.3em', marginTop: '0' }} {...props} />,
                    h2: ({node, ...props}) => <h2 style={{ color: headerColor, fontSize: '1.2em', marginTop: '1em' }} {...props} />,
                    h3: ({node, ...props}) => <h3 style={{ color: headerColor, fontSize: '1.1em', marginTop: '0.8em' }} {...props} />,
                    strong: ({node, ...props}) => <strong style={{ color: strongColor }} {...props} />,
                }}
            >
                {content}
            </ReactMarkdown>
        </div>
    );
});

// 🔥 [수정] theme prop 추가
const ProcessPopup = ({ content, position, onClose, theme }) => {
    const [isCopied, setIsCopied] = useState(false);
    const popupRef = useRef(null);
    const requestRef = useRef(null);

    const [popupState, setPopupState] = useState({
        x: 0, y: 0, width: 450, height: 500
    });
    
    const [isDragging, setIsDragging] = useState(false);
    const [isResizing, setIsResizing] = useState(false);
    const dragStart = useRef({ x: 0, y: 0 });
    const resizeStart = useRef({ w: 0, h: 0, x: 0, y: 0 });

    useLayoutEffect(() => {
        if (position) {
            const { innerWidth, innerHeight } = window;
            let newX = position.x + 20;
            let newY = position.y + 20;

            if (newX + 450 > innerWidth) newX = position.x - 450 - 20;
            if (newY + 500 > innerHeight) newY = position.y - 500 - 20;

            if (newX < 20) newX = 20;
            if (newY < 20) newY = 20;

            setPopupState(prev => ({ ...prev, x: newX, y: newY }));
        }
    }, [position]);

    const handleMouseDown = (e) => {
        e.stopPropagation();
        if (e.target.closest('.popup-actions') || e.target.closest('.resizer-handle')) return;
        setIsDragging(true);
        dragStart.current = { x: e.clientX - popupState.x, y: e.clientY - popupState.y };
    };

    const handleResizeDown = (e) => {
        e.stopPropagation();
        setIsResizing(true);
        resizeStart.current = { w: popupState.width, h: popupState.height, x: e.clientX, y: e.clientY };
    };

    useEffect(() => {
        const handleMouseMove = (e) => {
            if (isDragging) {
                if (requestRef.current) return;
                requestRef.current = requestAnimationFrame(() => {
                    setPopupState(prev => ({
                        ...prev,
                        x: e.clientX - dragStart.current.x,
                        y: e.clientY - dragStart.current.y
                    }));
                    requestRef.current = null;
                });
            }
            if (isResizing) {
                if (requestRef.current) return;
                requestRef.current = requestAnimationFrame(() => {
                    const dx = e.clientX - resizeStart.current.x;
                    const dy = e.clientY - resizeStart.current.y;
                    setPopupState(prev => ({
                        ...prev,
                        width: Math.max(300, resizeStart.current.w + dx),
                        height: Math.max(200, resizeStart.current.h + dy)
                    }));
                    requestRef.current = null;
                });
            }
        };

        const handleMouseUp = () => {
            setIsDragging(false);
            setIsResizing(false);
            if (requestRef.current) {
                cancelAnimationFrame(requestRef.current);
                requestRef.current = null;
            }
        };

        if (isDragging || isResizing) {
            window.addEventListener('mousemove', handleMouseMove);
            window.addEventListener('mouseup', handleMouseUp);
        }
        return () => {
            window.removeEventListener('mousemove', handleMouseMove);
            window.removeEventListener('mouseup', handleMouseUp);
            if (requestRef.current) cancelAnimationFrame(requestRef.current);
        };
    }, [isDragging, isResizing]);

    if (!content) return null;

    const safeContent = content.replace(/\n/g, '  \n');

    const handleCopy = async () => {
        try {
            await navigator.clipboard.writeText(content);
            setIsCopied(true);
            setTimeout(() => setIsCopied(false), 2000);
        } catch (err) { console.error(err); }
    };

    return (
        <div 
            ref={popupRef}
            className="process-popup" 
            // 🔥 [핵심] 테마 속성 주입 -> CSS 변수가 이것을 보고 변함
            data-theme={theme} 
            style={{ 
                top: popupState.y,
                left: popupState.x,
                width: popupState.width,
                height: popupState.height,
                cursor: isDragging ? 'grabbing' : 'default'
            }}
            onMouseDown={handleMouseDown}
            onClick={(e) => e.stopPropagation()}
        >
            <div className="popup-header">
                <div className="popup-title-area">
                    <span className="popup-icon"><Icons.FileText /></span>
                    <span className="popup-title">프로세스 정의서</span>
                </div>
                <div className="popup-actions" onMouseDown={e => e.stopPropagation()}>
                    <button className="icon-btn copy" onClick={handleCopy} title="복사">
                        {isCopied ? <Icons.Check /> : <Icons.Copy />}
                        <span>{isCopied ? "완료" : "복사"}</span>
                    </button>
                    <button className="icon-btn close" onClick={onClose} title="닫기">
                        <Icons.X />
                    </button>
                </div>
            </div>
            
            {/* 마크다운 뷰어에도 테마 전달 */}
            <MarkdownViewer content={safeContent} theme={theme} />

            <div className="resizer-handle" onMouseDown={handleResizeDown} />
        </div>
    );
};

export default ProcessPopup;
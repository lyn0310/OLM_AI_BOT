import React from 'react';

const ChatHeader = ({ 
    onClose, onClear, isDevMode, onDevModeToggle, 
    isSidebarOpen, onToggleSidebar, isMaximized, onToggleMaximize 
}) => {

    const handleAction = (e, callback) => {
        if (e) {
            e.stopPropagation();
            if (e.nativeEvent) e.nativeEvent.stopImmediatePropagation();
        }
        if (callback) callback();
    };

    return (
        <div 
            className="chat-header" 
            style={{ cursor: isMaximized ? 'default' : 'move' }}
        >
            <div className="header-left">
                {isMaximized && (
                    <button 
                        className="header-btn menu-btn" 
                        onMouseDown={(e) => e.stopPropagation()} 
                        onClick={(e) => handleAction(e, onToggleSidebar)}
                    >
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                            <line x1="3" y1="12" x2="21" y2="12"></line>
                            <line x1="3" y1="6" x2="21" y2="6"></line>
                            <line x1="3" y1="18" x2="21" y2="18"></line>
                        </svg>
                    </button>
                )}
                <div className="header-title">
                    OLM ChatBot
                    {isMaximized && (
                        <button 
                            className={`dev-toggle-btn ${isDevMode ? 'active' : ''}`}
                            onMouseDown={(e) => e.stopPropagation()}
                            onClick={(e) => handleAction(e, onDevModeToggle)}
                        >
                            {isDevMode ? 'DEV ON' : 'DEV OFF'}
                        </button>
                    )}
                </div>
            </div>
            
            <div className="header-controls">
                <button 
                    className="header-btn" 
                    onMouseDown={(e) => e.stopPropagation()}
                    onClick={(e) => handleAction(e, onClear)}
                >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                        <path d="M23 4v6h-6"></path><path d="M1 20v-6h6"></path>
                        <path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path>
                    </svg>
                </button>
                
                <button 
                    className="header-btn" 
                    onMouseDown={(e) => e.stopPropagation()}
                    onClick={(e) => handleAction(e, onToggleMaximize)}
                >
                    {isMaximized ? "❐" : "□"}
                </button>

                <button 
                    className="header-btn close-btn" 
                    onMouseDown={(e) => e.stopPropagation()}
                    onClick={(e) => handleAction(e, onClose)}
                >
                    ✕
                </button>
            </div>
        </div>
    );
};

export default ChatHeader;
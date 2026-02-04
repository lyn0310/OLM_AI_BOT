import React from 'react'
import ReactDOM from 'react-dom/client'
import ChatbotContainer from './widgets/Chatbot/ChatbotContainer.jsx'

const rootId = 'olm-chatbot-root';
let rootElement = document.getElementById(rootId);

if (!rootElement) {
  rootElement = document.createElement('div');
  rootElement.id = rootId;
  document.body.appendChild(rootElement);
}

ReactDOM.createRoot(rootElement).render(
  <React.StrictMode>
    <ChatbotContainer />
  </React.StrictMode>,
)
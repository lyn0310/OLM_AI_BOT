package com.xbolt.mcp.rag.service;

import com.xbolt.mcp.rag.domain.Message;
import com.xbolt.mcp.rag.domain.Session;
import com.xbolt.mcp.rag.repository.jpa.MessageRepository;
import com.xbolt.mcp.rag.repository.jpa.SessionRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;

    @Transactional("transactionManager")
    public String createSession(String title) {
        Session session = Session.create(title);
        sessionRepository.save(session);
        log.info("createSession : {}", session);
        return session.getId();
    }

    public List<Session> getSessions() {
        return sessionRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Message> getMessages(String sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional("transactionManager")
    public Message addMessage(String sessionId, String role, String content) {
        Message message = Message.create(sessionId, role, content);
        messageRepository.save(message);

        // Update session title if it's the first user message or if title is default
        if ("user".equalsIgnoreCase(role)) {
            List<Message> messages = getMessages(sessionId);
            sessionRepository.findById(sessionId).ifPresent(session -> {
                // If title is default "새로운 대화" or we just have few messages, try to set a
                // better title
                if ("새로운 대화".equals(session.getTitle()) || messages.size() <= 2) {
                    String titleContent = content != null ? content : "새로운 대화";
                    String shortTitle = titleContent.length() > 30 ? titleContent.substring(0, 30) + "..."
                            : titleContent;
                    session.setTitle(shortTitle);
                    sessionRepository.save(session);
                }
            });
        }
        return message;
    }

    @Transactional("transactionManager")
    public void deleteSession(String sessionId) {
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.deleteById(sessionId);
    }
}

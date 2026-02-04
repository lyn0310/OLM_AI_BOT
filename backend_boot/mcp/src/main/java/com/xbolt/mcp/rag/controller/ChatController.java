package com.xbolt.mcp.rag.controller;

import com.xbolt.mcp.rag.dto.ChatRequest;
import com.xbolt.mcp.rag.dto.ChatResponse;
import com.xbolt.mcp.rag.service.ChatService;
import com.xbolt.mcp.rag.service.HistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chat API", description = "메인 채팅 API (Python Backend Port)")
public class ChatController {

    private final ChatService chatService;
    private final HistoryService historyService;

    @PostMapping("")
    @Operation(summary = "채팅 (Graph Logic 포함)")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody ChatRequest request) {
        try {
            // Handle Session creation if null (Client might handle it, or we do same as
            // Python)
            String currentSessionId = request.getSessionId();
            log.info("Chat Request - Session ID: {}, Question: {}", currentSessionId, request.getMessage());

            if (currentSessionId == null || currentSessionId.trim().isEmpty()
                    || "null".equalsIgnoreCase(currentSessionId)) {
                log.info("Session ID is missing. Creating new session...");
                String initialMessage = request.getMessage();
                String title = (initialMessage != null && !initialMessage.isBlank()) ? initialMessage : "새로운 대화";
                if (title.length() > 30) {
                    title = title.substring(0, 30) + "...";
                }
                currentSessionId = historyService.createSession(title);
                log.info("Created new session ID: {}", currentSessionId);
            }

            ChatResponse response = chatService.chat(request.getMessage(), currentSessionId);

            // Mimic Python Response Structure
            return ResponseEntity.ok(Map.of(
                    "response", response.getAnswer(),
                    "graph_data", response.getGraph_data() != null ? response.getGraph_data() : Map.of(),
                    "sources",
                    response.getSources() != null ? response.getSources() : java.util.Collections.emptyList(),
                    "session_id", currentSessionId));
        } catch (Exception e) {
            log.error("Error during chat processing", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}

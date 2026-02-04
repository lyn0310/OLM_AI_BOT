package com.xbolt.mcp.rag.controller;

import com.xbolt.mcp.rag.domain.Message;
import com.xbolt.mcp.rag.domain.Session;
import com.xbolt.mcp.rag.service.HistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
@Tag(name = "Session API", description = "대화 세션 관리 API")
public class SessionController {

    private final HistoryService historyService;

    @GetMapping("")
    @Operation(summary = "모든 세션 조회")
    public ResponseEntity<List<Session>> getSessions() {
        return ResponseEntity.ok(historyService.getSessions());
    }

    @GetMapping("/{sessionId}")
    @Operation(summary = "세션 상세 메시지 조회")
    public ResponseEntity<List<Message>> getSessionMessages(@PathVariable String sessionId) {
        return ResponseEntity.ok(historyService.getMessages(sessionId));
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "세션 삭제")
    public ResponseEntity<Map<String, String>> deleteSession(@PathVariable String sessionId) {
        historyService.deleteSession(sessionId);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Session deleted"));
    }
}

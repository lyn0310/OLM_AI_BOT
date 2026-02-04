package com.xbolt.mcp.rag.controller;

import com.xbolt.mcp.rag.dto.ChatRequest;
import com.xbolt.mcp.rag.dto.ChatResponse;
import com.xbolt.mcp.rag.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
@Tag(name = "RAG API", description = "RAG 관련 API")
public class RagController {

    private final RagService ragService;

    @PostMapping("/graph_db/init")
    @Operation(summary = "Graph DB Build (Neo4j)")
    public Mono<ResponseEntity<String>> initGraphDb() throws Exception {
        return ragService.buildGraphDb()
                .then(Mono
                        .just(ResponseEntity.ok().body("{\"message\": \"Graph DB (Neo4j) initialized successfully\"}")))
                .onErrorResume(e -> Mono.just(
                        ResponseEntity.badRequest().body(e.getMessage())));
    }

    @PostMapping("/vector_db/init")
    @Operation(summary = "init")
    public Mono<ResponseEntity<String>> initVectorDb(@RequestParam(defaultValue = "false") boolean forceReload)
            throws Exception {
        return ragService.initVectorDbWithSpringAi()
                .then(Mono.just(
                        ResponseEntity.ok().body("{\"message\": \"Vector DB and QA Chain initialized successfully\"}")))
                .onErrorResume(e -> Mono.just(
                        ResponseEntity.badRequest().body(e.getMessage())));
    }

    @PostMapping("/vector_db/chat")
    @Operation(summary = "search")
    public Mono<ResponseEntity<ChatResponse>> queryChat(@RequestBody ChatRequest request) throws Exception {
        return ragService.search(request.getMessage())
                .map(answer -> ResponseEntity.ok(new ChatResponse(answer)))
                .onErrorResume(e -> Mono.just(
                        ResponseEntity.badRequest().body(new ChatResponse(e.getMessage()))));

    }

    /*
     * // 3. Vector DB에 저장된 문서 리스트 조회
     * 
     * @GetMapping("/list_documents")
     * public ResponseEntity<?> listDocuments() {
     * try {
     * var docs = ragService.listDocuments();
     * return ResponseEntity.ok(docs);
     * } catch (Exception e) {
     * return ResponseEntity.badRequest().body("{\"error\": \"" + e.getMessage() +
     * "\"}");
     * }
     * }
     */
}

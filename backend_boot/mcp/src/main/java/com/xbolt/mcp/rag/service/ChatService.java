package com.xbolt.mcp.rag.service;

import com.xbolt.mcp.rag.dto.ChatResponse;
import com.xbolt.mcp.rag.domain.Message;
import com.xbolt.mcp.rag.domain.Session;
import com.xbolt.mcp.rag.repository.jpa.MessageRepository;
import com.xbolt.mcp.rag.repository.jpa.SessionRepository;
import com.xbolt.mcp.rag.service.Graph.Neo4jGraphService;
import com.xbolt.mcp.rag.service.QaChain.QaChainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final Neo4jGraphService neo4jGraphService;
    private final QaChainService qaChainService;
    private final HistoryService historyService;

    // Regex for Korean entity extraction (Ported from Python)
    private static final Pattern RELATION_PATTERN = Pattern
            .compile("(.+?)\\s*(와|과|랑|하고|및)\\s*(.+?)\\s*(은|는|을|를|이|가)?\\s*(어떻게|어떤|연결|관계|흐름|순서|단계|경로).*$");

    public ChatResponse chat(String question, String sessionId) {
        // 1. Save User Message
        if (sessionId != null) {
            historyService.addMessage(sessionId, "user", question);
        }

        // 2. Logic Initialization
        List<String> contexts = new ArrayList<>();
        Map<String, Object> graphData = new HashMap<>(); // nodes, links
        graphData.put("nodes", new ArrayList<>());
        graphData.put("links", new ArrayList<>());

        // 3. Entity Extraction & Path Finding
        String graphAnswer = "";
        String[] entities = extractTwoEntities(question);

        if (entities != null) {
            String termA = entities[0];
            String termB = entities[1];
            log.info("Entities found: {} -> {}", termA, termB);

            // Find Candidates using Neo4j Fulltext Search (Replacing Vector Search)
            String id1 = findBestId(termA);
            String id2 = findBestId(termB);

            if (id1 != null && id2 != null) {
                // Find Path
                List<Map<String, Object>> pathResult = neo4jGraphService.findShortestPath(id1, id2);

                if (!pathResult.isEmpty()) {
                    Map<String, Object> pathRow = pathResult.get(0);
                    List<Map<String, Object>> nodes = (List<Map<String, Object>>) pathRow.get("nodes");

                    if (nodes != null && !nodes.isEmpty()) {
                        String names = nodes.stream()
                                .map(n -> (String) n.getOrDefault("name", n.get("id")))
                                .collect(Collectors.joining(" -> "));
                        graphAnswer = names;
                        contexts.add("Graph Path found: " + names);

                        // Populate visualization data
                        // For simplicity, just usage the path. A full implementation would fetch
                        // neighbors.
                        // Ideally call neo4jGraphService.getGraphVisualData(id1, 3) etc.
                        // Using start node of the path (id1) as the center for expansion
                        Map<String, Object> visualData = neo4jGraphService.getGraphVisualData(id1, 3);
                        graphData.put("nodes", visualData.get("nodes"));
                        graphData.put("links", visualData.get("links"));

                        // Also include path info
                        graphData.put("path", pathRow);
                    } else {
                        graphAnswer = "경로를 찾지 못했습니다.";
                    }
                } else {
                    graphAnswer = "경로를 찾지 못했습니다.";
                }
            }
        }

        // 4. Generate Answer
        String combinedQuestion = question;
        if (!graphAnswer.isEmpty()) {
            combinedQuestion += "\n[Graph Result]: " + graphAnswer;
        }

        // Note: We are not fetching text chunks from Vector DB here as requested
        // ("vector db part 빼고")
        // But we could fetch text from Neo4j nodes if `content` property exists.

        String answer = qaChainService.chat(combinedQuestion, contexts);

        // 5. Save Assistant Message
        if (sessionId != null) {
            historyService.addMessage(sessionId, "assistant", answer);
        }

        return ChatResponse.builder()
                .answer(answer)
                .graph_data(graphData)
                .sources(new ArrayList<>())
                .build();
    }

    private String[] extractTwoEntities(String text) {
        if (text == null)
            return null;
        Matcher m = RELATION_PATTERN.matcher(text);
        if (m.find()) {
            String a = m.group(1).trim();
            String b = m.group(3).trim();
            // Remove suffix if exists in group 3? Regex handles it via group 4 check
            // implied
            // Simplified cleanup
            return new String[] { a, b };
        }
        return null;
    }

    private String findBestId(String term) {
        List<Map<String, Object>> candidates = neo4jGraphService.findCandidates(term);
        if (candidates.isEmpty())
            return null;
        // Simple strategy: return first
        return (String) candidates.get(0).get("id");
    }
}

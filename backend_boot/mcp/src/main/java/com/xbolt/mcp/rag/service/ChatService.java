package com.xbolt.mcp.rag.service;

import com.xbolt.mcp.rag.dto.ChatResponse;
import com.xbolt.mcp.rag.domain.Message;
import com.xbolt.mcp.rag.domain.Session;
import com.xbolt.mcp.rag.repository.jpa.MessageRepository;
import com.xbolt.mcp.rag.repository.jpa.SessionRepository;
import com.xbolt.mcp.rag.service.History.HistoryService;
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
    private final com.xbolt.mcp.rag.service.Graph.CypherGenerationService cypherGenerationService;

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

        String pathMode = cypherGenerationService.detectPathMode(question);

        if (entities != null && (!"none".equals(pathMode) || entities.length == 2)) {
            String termA = entities[0];
            String termB = entities[1];
            log.info("Entities found: {} -> {}", termA, termB);

            // Find Candidates using Neo4j Fulltext Search (Replacing Vector Search)
            String id1 = findBestId(termA);
            String id2 = findBestId(termB);

            if (id1 != null && id2 != null) {
                Map<String, Object> pathResultMap = null;

                // 3-1. Try Dynamic Cypher Generation first if mode is specific
                if (!"none".equals(pathMode)) {
                    String cypher = cypherGenerationService.generatePathCypher(question, pathMode);
                    // Inject IDs into the query constraints (or rely on LLM if it put placeholders?
                    // Template uses $fromId, $toId)
                    // The template expects params.
                    cypher = cypherGenerationService.wrapPathCypher(cypher);

                    Map<String, Object> params = new HashMap<>();
                    params.put("fromId", id1);
                    params.put("toId", id2);
                    params.put("maxHops", 20);

                    // Simple conditional check wrapper
                    if ("conditional".equals(pathMode)) {
                        Pattern passPattern = Pattern.compile("(.+?)\\s*(?:을|를)?\\s*(?:거쳐|지나|경유|포함|반드시)");
                        Matcher m = passPattern.matcher(question);
                        if (m.find()) {
                            // The regex might catch "A와 B 사이의 C를 거쳐서" -> group 1 is "A와 B 사이의 C"
                            // We need to be careful.
                            // Maybe just take the immediate words before it?
                            // Or relying on the fact that A and B are extracted separately.
                            // Ideally we strip A and B from the string first? No.

                            // Let's take the capture group 1, trim it.
                            // If it's too long, it might be the whole sentence.
                            // "BP 1물 1코드 Rule Set 정의 지나는"
                            // The match will stop at "지나는". Group 1 is "BP 1물 1코드 Rule Set 정의"
                            // This looks correct.
                            String candidate = m.group(1).trim();

                            // Clean up if it contains A or B?
                            // For now, let's just try to find an ID for it.

                            // Taking the last few words might be safer if it matches the whole "Start to
                            // End via Middle" string.
                            // But `extractTwoEntities` regex is specific.
                            // Let's assume the user query structure is relatively simple or the
                            // `findBestId` can handle the full string.

                            // In the user case: "BP 1물 1코드 Rule Set 정의 지나는" -> candidate="BP 1물 1코드 Rule
                            // Set 정의"
                            // That is exactly the node name.

                            String mid = findBestId(candidate);
                            if (mid != null) {
                                params.put("mustPassId", mid);
                                log.info("Conditional Path: Must pass '{}' ({})", candidate, mid);
                            }
                        }
                    }

                    pathResultMap = neo4jGraphService.executePathQuery(cypher, params);
                }

                // 3-2. Fallback to Static Shortest Path if dynamic failed or mode is 'none'
                // (but we have 2 entities)
                if ((pathResultMap == null || ((List) pathResultMap.get("nodes")).isEmpty())) {
                    List<Map<String, Object>> staticResult = neo4jGraphService.findShortestPath(id1, id2);
                    if (!staticResult.isEmpty()) {
                        Map<String, Object> row = staticResult.get(0);
                        pathResultMap = new HashMap<>();
                        pathResultMap.put("nodes", row.get("nodes"));
                        pathResultMap.put("links", row.get("links"));
                    }
                }

                if (pathResultMap != null && !((List) pathResultMap.get("nodes")).isEmpty()) {
                    List<Map<String, Object>> nodes = (List<Map<String, Object>>) pathResultMap.get("nodes");
                    List<Map<String, Object>> links = (List<Map<String, Object>>) pathResultMap.get("links");

                    String names = nodes.stream()
                            .map(n -> (String) n.getOrDefault("name", n.get("id")))
                            .collect(Collectors.joining(" -> "));
                    graphAnswer = names;
                    contexts.add("Graph Path found: " + names);

                    // Populate visualization data
                    // Using start node of the path (id1) as the center for expansion
                    Map<String, Object> visualData = neo4jGraphService.getGraphVisualData(id1, 4);
                    graphData.put("nodes", visualData.get("nodes"));
                    graphData.put("links", visualData.get("links"));

                    // Also include path info
                    graphData.put("path", pathResultMap);
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

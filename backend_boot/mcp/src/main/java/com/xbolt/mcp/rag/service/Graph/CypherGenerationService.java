package com.xbolt.mcp.rag.service.Graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CypherGenerationService {

    private final ChatModel chatModel;
    private final Neo4jGraphService neo4jGraphService;

    private static final String PATH_CYPHER_TEMPLATE = """
            You are a Neo4j Cypher generator for PATH queries ONLY.

            Schema:
            %s

            Hard Rules (must follow):
            1) Use label :Process only.
            2) Match nodes by id property only (GlobalKey). Never use name for matching.
            3) You MUST return a single path variable named p: RETURN p
            4) Never write any explanation. Output ONLY Cypher.
            5) Read-only query only. Do NOT use: CREATE, MERGE, DELETE, SET, CALL dbms, LOAD CSV, apoc.* except explicitly allowed below.

            Allowed patterns by mode:
            - shortest:
            MATCH (a:Process {id: $fromId}), (b:Process {id: $toId})
            MATCH p = shortestPath((a)-[:RELATED_TO*..15]-(b))
            RETURN p

            - longest:
            MATCH (a:Process {id: $fromId}), (b:Process {id: $toId})
            MATCH p = (a)-[:RELATED_TO*1..$maxHops]-(b)
            WHERE size(nodes(p)) = size(apoc.coll.toSet(nodes(p)))
            RETURN p
            ORDER BY length(p) DESC
            LIMIT 1

            - conditional (must pass $mustPassId):
            MATCH (a:Process {id: $fromId}), (b:Process {id: $toId})
            MATCH p = (a)-[:RELATED_TO*..$maxHops]->(b)
            WHERE any(n IN nodes(p) WHERE n.id = $mustPassId)
            RETURN p
            LIMIT 1

            User Question:
            %s
            """;

    public String detectPathMode(String userInput) {
        if (userInput == null)
            return "none";
        String q = userInput.trim();

        // Longest
        if (containsAny(q, "최장", "가장 긴", "최대", "최장거리", "가장 먼")) {
            return "longest";
        }
        // Conditional
        if (containsAny(q, "거쳐", "거쳐서", "반드시", "포함", "must", "경유")) {
            return "conditional";
        }
        // Shortest / Path
        if (containsAny(q, "최단", "경로", "흐름", "순서", "단계", "A부터", "부터", "까지")) {
            return "shortest";
        }
        return "none";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k))
                return true;
        }
        return false;
    }

    public String generatePathCypher(String question, String mode) {
        // Simple schema description
        String schema = "Node labels: [Process]\nRelationship types: [RELATED_TO]\nProperty keys: [id, name, content]";

        String inputPrompt = String.format("[mode=%s] %s", mode, question);
        String finalPrompt = String.format(PATH_CYPHER_TEMPLATE, schema, inputPrompt);

        SystemMessage systemMsg = new SystemMessage("Return ONLY Cypher. No markdown. No explanation.");
        UserMessage userMsg = new UserMessage(finalPrompt);

        try {
            String cypher = chatModel.call(new Prompt(List.of(systemMsg, userMsg))).getResult().getOutput().getText();
            return cleanCypher(cypher);
        } catch (Exception e) {
            log.error("Cypher generation failed", e);
            return null;
        }
    }

    public String wrapPathCypher(String cypher) {
        if (cypher == null || cypher.isBlank())
            return cypher;

        // Using regex to check for 'RETURN p'
        Pattern p = Pattern.compile("(?i)\\bRETURN\\s+p\\b.*$");
        Matcher m = p.matcher(cypher);

        if (!m.find())
            return cypher;

        String base = cypher.substring(0, m.start()).trim();

        String wrappedReturn = """
                RETURN
                p,
                [n IN nodes(p) | {
                    id: coalesce(n.id, elementId(n)),
                    name: coalesce(n.name, n.id, elementId(n))
                }] AS ns,
                [r IN relationships(p) | {
                    source: coalesce(startNode(r).id, elementId(startNode(r))),
                    target: coalesce(endNode(r).id, elementId(endNode(r))),
                    label: coalesce(r.label, type(r))
                }] AS rs
                """;

        return base + "\n" + wrappedReturn;
    }

    private String cleanCypher(String text) {
        if (text == null)
            return null;
        return text.replaceAll("```cypher", "")
                .replaceAll("```", "")
                .trim();
    }
}

package com.xbolt.mcp.rag.service.Graph;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.springframework.stereotype.Service;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jGraphService {
    private final Driver driver;

    public void createSchema() {
        try (var session = driver.session()) {
            session.run("CREATE CONSTRAINT process_id IF NOT EXISTS FOR (p:Process) REQUIRE p.id IS UNIQUE");
            session.run("CREATE CONSTRAINT attr_id IF NOT EXISTS FOR (a:Attribute) REQUIRE a.id IS UNIQUE");
            session.run("CREATE INDEX process_display_id_idx IF NOT EXISTS FOR (p:Process) ON (p.display_id)");
            session.run("CREATE FULLTEXT INDEX process_name_idx IF NOT EXISTS FOR (n:Process) ON EACH [n.name, n.id]");
        }
    }

    public void upsertBatch(List<Map<String, Object>> batchParams) {
        if (batchParams == null || batchParams.isEmpty())
            return;

        try (var session = driver.session()) {
            session.executeWrite(tx -> {
                // UNWIND를 통한 배치 처리
                tx.run("""
                            UNWIND $batch AS item

                            MERGE (p:Process {id: item.gkey})
                            SET p.name = item.pname,
                                p.display_id = item.pid,
                                p.source = item.fname,
                                p.content = item.content,
                                p.updatedAt = datetime()

                            FOREACH (attr IN coalesce(item.attributes, []) |
                                MERGE (a:Attribute {id: item.gkey + '_' + attr.key})
                                SET a.name = attr.key, a.value = attr.value
                                MERGE (p)-[:HAS_ATTRIBUTE]->(a)
                            )

                            FOREACH (rel IN coalesce(item.relations, []) |
                                MERGE (r:Process {id: rel.rel_gkey})
                                ON CREATE SET r.name = rel.rel_name
                                MERGE (p)-[link:RELATED_TO]->(r)
                                SET link.label = rel.rel_label
                            )
                        """, Map.of("batch", batchParams));
                return null;
            });
        } catch (Exception e) {
            log.error("Batch upsert failed for a chunk of size: {}", batchParams.size(), e);
            throw e;
        }
    }

    public void clearAll() {
        try (var session = driver.session()) {
            session.run("MATCH (n) DETACH DELETE n");
        }
    }

    // ==================================================================================
    // READ OPERATIONS for Chat
    // ==================================================================================

    public List<Map<String, Object>> findCandidates(String term) {
        if (term == null || term.isBlank())
            return Collections.emptyList();

        // Escape Lucene special characters if needed, simplified for now
        String query = "CALL db.index.fulltext.queryNodes(\"process_name_idx\", $term) YIELD node, score " +
                "RETURN node.id AS id, node.name AS name, score " +
                "ORDER BY score DESC LIMIT 5";

        try (var session = driver.session()) {
            return session.run(query, Map.of("term", term)).list(r -> r.asMap());
        } catch (Exception e) {
            log.error("Neo4j findCandidates error", e);
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> findShortestPath(String fromId, String toId) {
        String query = """
                    MATCH (a:Process {id: $fromId}), (b:Process {id: $toId})
                    MATCH p = shortestPath((a)-[:RELATED_TO*..8]->(b))
                    RETURN
                        [n IN nodes(p) | {id: n.id, name: n.name}] AS nodes,
                        [r IN relationships(p) | {source: startNode(r).id, target: endNode(r).id, label: type(r)}] AS links
                """;

        try (var session = driver.session()) {
            var result = session.run(query, Map.of("fromId", fromId, "toId", toId));
            if (result.hasNext()) {
                return List.of(result.next().asMap());
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Neo4j path finding error", e);
            return Collections.emptyList();
        }
    }

    public Map<String, Object> getGraphVisualData(String rootId, int depth) {
        // Refined Query: 4-hop expansion (default), Depth-based Grouping, Specific Link
        // Labels
        // Depth 1 -> Group 2, Depth 2 -> Group 3, etc. (Root is Group 1)

        String query = """
                MATCH (root:Process {id: $rootId})

                // Level 1
                OPTIONAL MATCH (root)-[r1]-(n1:Process)

                // Level 2
                OPTIONAL MATCH (n1)-[r2]-(n2:Process)

                // Level 3
                OPTIONAL MATCH (n2)-[r3]-(n3:Process)

                // Level 4
                OPTIONAL MATCH (n3)-[r4]-(n4:Process)

                WITH root,
                     collect(DISTINCT r1) as rels1, collect(DISTINCT n1) as nodes1,
                     collect(DISTINCT r2) as rels2, collect(DISTINCT n2) as nodes2,
                     collect(DISTINCT r3) as rels3, collect(DISTINCT n3) as nodes3,
                     collect(DISTINCT r4) as rels4, collect(DISTINCT n4) as nodes4

                // Assign groups based on depth
                WITH [ {n: root, group: 1} ] +
                     [x IN nodes1 | {n: x, group: 2}] +
                     [x IN nodes2 | {n: x, group: 3}] +
                     [x IN nodes3 | {n: x, group: 4}] +
                     [x IN nodes4 | {n: x, group: 5}] as complexNodes,
                     rels1 + rels2 + rels3 + rels4 as allRels

                // Unwind and Deduplicate Nodes (keep min group)
                UNWIND complexNodes as item
                WITH item.n as n, item.group as group, allRels
                WHERE n IS NOT NULL

                WITH n.id as id, n.name as name, n.content as content, min(group) as finalGroup, allRels

                WITH collect({
                    id: id,
                    name: name,
                    content: content,
                    group: finalGroup
                }) as nodes, allRels

                // Unwind and Deduplicate Links
                UNWIND allRels as r
                WITH nodes, r
                WHERE r IS NOT NULL

                // Use r.label if available, else type(r)
                RETURN nodes, collect(DISTINCT {
                    source: startNode(r).id,
                    target: endNode(r).id,
                    label: coalesce(r.label, type(r))
                }) as links
                """;

        try (var session = driver.session()) {
            var result = session.run(query, Map.of("rootId", rootId));
            if (result.hasNext()) {
                return result.next().asMap();
            }
            return Map.of("nodes", List.of(), "links", List.of());
        } catch (Exception e) {
            log.error("Neo4j getGraphVisualData error", e);
            return Map.of("nodes", List.of(), "links", List.of());
        }
    }
}
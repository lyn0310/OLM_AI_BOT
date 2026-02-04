import os
import re
import chromadb
from openai import OpenAI

from .intent_router import IntentRouter

from langchain_neo4j import Neo4jGraph
from langchain_openai import ChatOpenAI
from ..core.config import API_KEY, DATABASE_DIR, MODEL_CHAT, MODEL_EMBEDDING, NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD
import logging

logging.getLogger("langchain").setLevel(logging.WARNING)
logging.getLogger("langchain_community").setLevel(logging.WARNING)
logging.getLogger("langchain_openai").setLevel(logging.WARNING)

logging.getLogger("neo4j").setLevel(logging.ERROR)
client = OpenAI(api_key=API_KEY.strip())

class UnifiedRAGChatBot:
    def __init__(self):
        try:
            # 1. Graph DB 연결
            self.graph = Neo4jGraph(url=NEO4J_URI, username=NEO4J_USERNAME, password=NEO4J_PASSWORD)
            self.graph.refresh_schema()

            llm = ChatOpenAI(model=MODEL_CHAT, api_key=API_KEY, temperature=0)

            self.PATH_CYPHER_TEMPLATE = """
            You are a Neo4j Cypher generator for PATH queries ONLY.

            Schema:
            {schema}

            Hard Rules (must follow):
            1) Use label :Process only.
            2) Match nodes by id property only (GlobalKey). Never use name for matching.
            3) You MUST return a single path variable named p: RETURN p
            4) Never write any explanation. Output ONLY Cypher.
            5) Read-only query only. Do NOT use: CREATE, MERGE, DELETE, SET, CALL dbms, LOAD CSV, apoc.* except explicitly allowed below.

            Allowed patterns by mode:
            - shortest:
            MATCH (a:Process {{id: $fromId}}), (b:Process {{id: $toId}})
            MATCH p = shortestPath((a)-[:RELATED_TO*..8]->(b))
            RETURN p

            - longest (uses APOC):
            MATCH (a:Process {{id: $fromId}}), (b:Process {{id: $toId}})
            CALL apoc.algo.allSimplePaths(a, b, 'RELATED_TO>', $maxHops) YIELD path
            WITH path AS p
            RETURN p
            ORDER BY length(p) DESC
            LIMIT 1

            - conditional (must pass $mustPassId):
            MATCH (a:Process {{id: $fromId}}), (b:Process {{id: $toId}})
            MATCH p = (a)-[:RELATED_TO*..$maxHops]->(b)
            WHERE any(n IN nodes(p) WHERE n.id = $mustPassId)
            RETURN p
            LIMIT 1

            User Question:
            {question}
            """.strip()

            print("[Bot] Graph DB Connected Successfully.")

        except Exception as e:
            self.graph_chain = None
            print(f"[Bot] Graph DB Error: {e}")

        self.reload_db()
        self.router = IntentRouter(client=client, model=MODEL_CHAT)
        self.system_prompt = "너는 하림그룹 PI 프로젝트 도우미야. 최대한 간결하게 요약해서 답해."

    def _wrap_path_cypher(self, cypher: str) -> str:
        """
        LLM이 만든 'RETURN p' path cypher를
        'RETURN p, ns, rs' 로 강제 변환해서 파싱 안정화.

        핵심: p가 Path로 hydration 안돼도 ns/rs는 dict/list로 안전하게 들어온다.
        """
        if not cypher:
            return cypher

        m = re.search(r"(?is)\bRETURN\s+p\b.*$", cypher)
        if not m:
            return cypher

        base = cypher[:m.start()].rstrip()

        wrapped_return = """
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
        """.strip()

        return f"{base}\n{wrapped_return}"

    def reload_db(self):
        self.process_col = self._load_col("process_db", "process_collection")
        self.project_col = self._load_col("project_db", "project_collection")

    def _load_col(self, folder, col_name):
        path = os.path.join(DATABASE_DIR, folder)
        if os.path.exists(path):
            return chromadb.PersistentClient(path=path).get_collection(col_name)
        return None

    def get_embedding(self, text):
        return client.embeddings.create(input=[text.replace("\n", " ")], model=MODEL_EMBEDDING).data[0].embedding

    def _extract_active_context(self, chat_history):
        if not chat_history: return None
        for msg in reversed(chat_history):
            if msg['role'] == 'assistant':
                match = re.search(r"\[([\d\.]+_?[^\]]+)\]", msg['content'])
                if match: return match.group(1)
                match_header = re.search(r"#\s*([\d\.]+_?[^\n]+)", msg['content'])
                if match_header: return match_header.group(1)
        return None

    def _rewrite_query(self, user_input, chat_history, active_context):
        query = user_input
        if "서브프로세스" in query: query = query.replace("서브프로세스", "Sub Process")
        if "액티비티" in query: query = query.replace("액티비티", "Activity")
        if active_context: return f"항목 {active_context} 에 대한 {query}"
        return query

    def _is_flow_question(self, q: str) -> bool:
        keywords = ["흐름", "단계", "순서", "경로", "어떻게 연결", "어떻게 연결돼", "연결되어", "관계"]

        return any(k in q for k in keywords)

    def _pick_best_candidate(self, term: str, candidates: list):
        if not candidates: return None
        term = term.strip()

        # 1순위: Exact Match
        for c in candidates:
            if term == (c.get("name") or "").strip():
                return c

        # 2순위: Partial Match
        partials = [c for c in candidates if term in (c.get("name") or "")]
        if partials:
            best_partial = sorted(partials, key=lambda x: len(x.get("name", "")))[0]
            return best_partial

        # 3순위: ID에 키워드가 포함된 경우
        for c in candidates:
            if term in (c.get("id") or ""):
                return c

        # 4순위: 점수 순
        return sorted(candidates, key=lambda x: x.get("score", 0), reverse=True)[0]

    def _extract_two_entities(self, q: str):
        if not q:
            return (None, None)

        q = q.strip()

        # 1) 한글 접속사 기반
        patterns = [
            r"(.+?)\s*(와|과|랑|하고|및)\s*(.+?)\s*(은|는|을|를|이|가)?\s*(어떻게|어떤|연결|관계|흐름|순서|단계|경로).*$",
            r"(.+?)\s*(와|과|랑|하고|및)\s*(.+)$",
        ]
        for pat in patterns:
            m = re.match(pat, q)
            if m:
                a = (m.group(1) or "").strip()
                b = (m.group(3) or "").strip()
                b = re.sub(r"(은|는|을|를|이|가)\s*$", "", b).strip()
                return (a, b)

        # 2) 콤마/슬래시 구분
        if "," in q:
            parts = [p.strip() for p in q.split(",") if p.strip()]
            if len(parts) >= 2:
                return (parts[0], parts[1])

        return (None, None)

    def _resolve_process_candidates(self, term: str, limit: int = 5):
        term = (term or "").strip()
        if not term or not self.graph:
            return []

        safe_term = re.sub(r'["\\]', " ", term).strip()

        query = """
        CALL db.index.fulltext.queryNodes("process_name_idx", $q) YIELD node, score
        RETURN node.id AS id, node.name AS name, score
        ORDER BY score DESC
        LIMIT $limit
        """
        try:
            rows = self.graph.query(query, {"q": safe_term, "limit": int(limit)})
            return rows or []
        except Exception as e:
            print(f"[ERROR] resolve candidates error: {e}")
            return []


    # ============================================================
    # [3] 시각화 데이터 추출 
    # ============================================================
    def get_graph_visual_data(self, user_input, related_pids=[], full_text_map={}, path_nodes=[]):
        if not self.graph or not related_pids: return {"nodes": [], "links": []}

        depth_match = re.search(r'(\d+)\s*(단계|depth|뎁스)', user_input)
        requested_depth = int(depth_match.group(1)) if depth_match else 3
        max_viz_depth = min(max(requested_depth, 1), 6) 
        
        target_pid = str(related_pids[0]).strip()

        # 2. 동적 쿼리 빌드 
        match_clauses = [
            "MATCH (root:Process) WHERE root.id CONTAINS $targetPid OR root.display_id CONTAINS $targetPid WITH root LIMIT 1",
            "OPTIONAL MATCH (root)-[attr_r:HAS_ATTRIBUTE]->(a:Attribute)"
        ]
        return_parts = ["root", "collect(DISTINCT {id: a.id, name: a.name, val: a.value, group: 5}) as attrs"]

        for i in range(1, max_viz_depth + 1):
            prev = "root" if i == 1 else f"node{i-1}"
            match_clauses.append(f"OPTIONAL MATCH ({prev})-[rel{i}:RELATED_TO]->(node{i}:Process)")
            return_parts.append(f"collect(DISTINCT node{i}) as nodes{i}")
            return_parts.append(f"collect(DISTINCT {{source: startNode(rel{i}).id, target: endNode(rel{i}).id, label: rel{i}.label}}) as rels{i}")

        full_query = "\n".join(match_clauses) + "\nRETURN " + ", ".join(return_parts)

        try:
            results = self.graph.query(full_query, {"targetPid": target_pid})
            if not results: return {"nodes": [], "links": []}
            record = results[0]

            nodes_dict, links = {}, set()
            all_node_ids = set()

            # 3. ID 수집 
            if path_nodes:
                for n in path_nodes:
                    nid = self._normalize_pid(n)
                    if nid:
                        all_node_ids.add(nid)
            
            root = record.get("root") or {}
            root_id = root.get("id")
            if root_id:
                all_node_ids.add(root_id)
            
            for i in range(1, max_viz_depth + 1):
                level_nodes = record.get(f'nodes{i}') or []
                for n in level_nodes:
                    if n and n.get('id'): all_node_ids.add(n['id'])

            # 4. ChromaDB 연동 
            if self.process_col:
                missing_ids = [nid for nid in all_node_ids if nid not in full_text_map]
                for mid in missing_ids:
                    short_id = mid.split('_')[0] if '_' in mid else mid
                    res = self.process_col.get(where={"process_id": short_id.strip()})
                    if not res['documents']:
                        res = self.process_col.get(where={"process_id": mid.strip()})
                    if not res['documents']:
                        res = self.process_col.get(where={"source": mid.strip() + ".md"})
                    if res['documents']:
                        full_text_map[mid] = "\n\n---\n\n".join(res['documents'])

            for p_node in path_nodes:
                pid = self._normalize_pid(p_node)
                if not pid:
                    continue
                if pid not in nodes_dict:
                    p_content = full_text_map.get(pid, "")

                    p_name = None
                    if isinstance(p_node, dict):
                        p_name = p_node.get("name")

                    nodes_dict[pid] = {
                        "id": pid,
                        "name": p_name or pid,
                        "group": 1,
                        "val": 15,
                        "content": f"# {p_name or pid}\n\n{p_content}" if p_content else f"# {pid}\n\n정보없음"
                    }

            if path_nodes and len(path_nodes) > 1:
                for i in range(len(path_nodes) - 1):
                    s = self._normalize_pid(path_nodes[i])
                    t = self._normalize_pid(path_nodes[i + 1])
                    if s and t:
                        links.add((s, t, "PATH"))

            # 6. 주변 노드 병합 
            # Group 1 (Root)
            root = record.get("root") or {}
            root_id = root.get("id")
            root_name = root.get("name") or root_id

            if root_id and root_id not in nodes_dict:
                r_content = full_text_map.get(root_id, "")
                nodes_dict[root_id] = {
                    "id": root_id,
                    "name": root_name,
                    "group": 1,
                    "val": 10,
                    "content": f"# {root_name}\n\n{r_content}"
                }

            # 단계별 노드 및 링크
            for i in range(1, max_viz_depth + 1):
                for n in (record.get(f'nodes{i}') or []):
                    if n and n.get('id') and n['id'] not in nodes_dict:
                        n_id, n_content = n['id'], full_text_map.get(n['id'], "")
                        nodes_dict[n_id] = {"id": n_id, "name": n['name'], "group": i+2, "val": 10, "content": f"# {n['name']}\n\n{n_content}"}
                
                for rel in (record.get(f'rels{i}') or []):
                    if rel.get('source') and rel.get('target'):
                        links.add((rel['source'], rel['target'], rel.get('label', '연관')))

            formatted_links = [{"source": s, "target": t, "label": l} for s, t, l in links]
            return {"nodes": list(nodes_dict.values()), "links": formatted_links}

        except Exception as e:
            print(f"[ERROR] Dynamic Error: {e}"); return {"nodes": [], "links": []}

    def chat(self, user_input, chat_history=[]):
        # 0. 초기화
        full_text_map = {}
        path_payload = {"nodes": [], "links": []}
        graph_answer = ""
        context_list = []
        vector_items = []

        # 1. 의도 파악 및 도메인 체크
        route_res = self.router.route(user_input)
        domain_keywords = ["프로세스", "BP", "특성", "품목", "코드", "관리", "연결", "경로"]
        is_domain_question = any(k in user_input for k in domain_keywords)
        current_route = "both" if is_domain_question else route_res.route

        if current_route == "chat":
            return {"response": "안녕하세요! 무엇을 도와드릴까요?", "graph_data": {"nodes":[], "links":[], "path":{"nodes":[], "links":[]}}}

        # 2. [개선된 검색] 파트너님의 매칭 로직 적용
        a_term, b_term = self._extract_two_entities(user_input)

        if a_term and b_term:
            print(f"[INFO] 질문 분리 분석: [{a_term}] -> [{b_term}]")
            # 각 용어에 대해 정확한 후보 선출
            for term in [a_term, b_term]:
                # 1) Vector DB에서 후보군 5개 추출
                query_emb = self.get_embedding(term)
                res = self.process_col.query(query_embeddings=[query_emb], n_results=5)
                
                candidates = []
                for i in range(len(res['metadatas'][0])):
                    meta = res['metadatas'][0][i]
                    candidates.append({
                        "id": meta.get('global_key'),
                        "name": meta.get('section', ''), # 항목 명칭
                        "score": 1.0 # 필요시 유사도 점수 활용 가능
                    })
                
                # 2) [파트너님 로직] 가장 적합한 후보 하나 선택
                best = self._pick_best_candidate(term, candidates)
                if best:
                    vector_items.append(best)
                    # 텍스트 맵 및 컨텍스트 채우기
                    gkey = best['id']
                    res_all = self.process_col.get(where={"global_key": gkey})
                    if res_all['documents']:
                        full_text_map[gkey] = res_all['documents'][0]
                        context_list.append(res_all['documents'][0])
        
        # 분리가 안 된 경우 fallback 검색 (기존 로직)
        if not vector_items:
            res = self.process_col.query(query_embeddings=[self.get_embedding(user_input)], n_results=3)
            for i in range(len(res['documents'][0])):
                meta = res['metadatas'][0][i]
                gkey = meta.get('global_key')
                if gkey:
                    vector_items.append({"id": gkey, "name": meta.get('section', '')})
                    full_text_map[gkey] = res['documents'][0][i]
                    context_list.append(res['documents'][0][i])

        # 3. [Graph Search] 확정된 ID로 쿼리 실행
        mode = self._detect_path_mode(user_input)

        if current_route in ("graph", "both") and len(vector_items) >= 2 and mode != "none":
            id1 = vector_items[0]["id"]  # GlobalKey
            id2 = vector_items[1]["id"]  # GlobalKey

            params = {
                "fromId": id1,
                "toId": id2,
                "maxHops": 8
            }

            if mode == "conditional":
                must_pass_term = None

                # 예: "A부터 B까지 C를 거쳐서", "C를 경유해서", "C 포함해서"
                m = re.search(r"(거쳐서|경유해서|경유|반드시|포함해서|포함)\s*([^\s,]+)", user_input)
                if m:
                    must_pass_term = (m.group(2) or "").strip()

                if must_pass_term and self.process_col:
                    try:
                        emb = self.get_embedding(must_pass_term)
                        r = self.process_col.query(query_embeddings=[emb], n_results=5)
                        mp = None
                        for i in range(len(r.get("metadatas", [[]])[0])):
                            gk = r["metadatas"][0][i].get("global_key")
                            if gk:
                                mp = gk
                                break
                        if mp:
                            params["mustPassId"] = mp
                    except Exception as e:
                        print(f"[WARN] mustPass resolve error: {e}")

                if "mustPassId" not in params:
                    mode = "shortest"

            # -------------------------------
            # generate → validate → execute → parse
            # -------------------------------
            try:
                gen_question = f"Find {mode} path from fromId=$fromId to toId=$toId with maxHops=$maxHops"
                if mode == "conditional":
                    gen_question += " and mustPassId=$mustPassId"

                cypher = self._generate_path_cypher(question=gen_question, mode=mode)
                exec_cypher = self._wrap_path_cypher(cypher)
                print("DEBUG cypher =", exec_cypher)

                self._validate_path_cypher(exec_cypher)

                rows = self.graph.query(exec_cypher, params)
                print("DEBUG rows type =", type(rows), "len =", (len(rows) if rows else 0))

                if rows:
                    p0 = rows[0].get("p") if isinstance(rows[0], dict) else None
                    print("DEBUG p type =", type(p0), "isNone=", (p0 is None))

                self._extract_path_from_neo4j_rows(rows, path_payload)
                print("DEBUG parsed nodes =", len(path_payload.get("nodes", [])))

                if path_payload["nodes"]:
                    names = [n.get("name") or n.get("id") for n in path_payload["nodes"]]
                    graph_answer = " → ".join(names)
                else:
                    graph_answer = "경로를 찾지 못했습니다."

            except Exception as e:
                import traceback
                traceback.print_exc()
                print(f"[ERROR] text2cypher path error: {e}")
                graph_answer = "경로 탐색 중 오류가 발생했습니다."

        # 4. 답변 합성 및 시각화 송출
        context_text = "\n\n---\n\n".join(context_list)
        messages = [
            {"role": "system", "content": self.system_prompt + "\n제공된 정의서와 그래프 경로를 통합해서 답해."},
            {"role": "user", "content": f"[그래프 결과]: {graph_answer}\n\n[상세 정의서]: {context_text}\n\n질문: {user_input}"}
        ]
        
        try:
            response = client.chat.completions.create(model=MODEL_CHAT, messages=messages, temperature=0)
            final_answer = response.choices[0].message.content
        except:
            final_answer = graph_answer or "정보를 찾을 수 없습니다."

        final_answer = self._sanitize_answer(final_answer)

        target_visual = [vector_items[0]['id']] if vector_items else []
        visual_data = self.get_graph_visual_data(
            user_input=user_input, 
            related_pids=target_visual, 
            full_text_map=full_text_map, 
            path_nodes=path_payload["nodes"] 
        )
        visual_data["path"] = path_payload

        existing = set(
            (l.get("source"), l.get("target"), l.get("label"))
            for l in (visual_data.get("links") or [])
            if l.get("source") and l.get("target")
        )

        for pl in (path_payload.get("links") or []):
            s, t = pl.get("source"), pl.get("target")
            if not s or not t:
                continue
            key = (s, t, "PATH")
            if key in existing:
                continue
            visual_data["links"].append({
                "source": s,
                "target": t,
                "label": "PATH",
                "isPath": True
            })
            existing.add(key)

        return {"response": final_answer, "sources": [], "graph_data": visual_data}

    def _extract_path_from_neo4j_rows(self, rows, path_payload):
        """
        rows: self.graph.query() 결과
        기대: RETURN p, ns, rs 를 받는다 (wrap_path_cypher로 강제)
        """
        if not rows or not isinstance(rows, list) or not isinstance(rows[0], dict):
            return

        row0 = rows[0]

        ns = row0.get("ns")
        rs = row0.get("rs")

        if isinstance(ns, list) and isinstance(rs, list):
            path_payload["nodes"] = []
            path_payload["links"] = []

            seen = set()
            for n in ns:
                if not isinstance(n, dict):
                    continue
                nid = str(n.get("id") or "").strip()
                if not nid or nid in seen:
                    continue
                nname = (n.get("name") or nid)
                path_payload["nodes"].append({"id": nid, "name": nname})
                seen.add(nid)

            for r in rs:
                if not isinstance(r, dict):
                    continue
                s = r.get("source")
                t = r.get("target")
                if not s or not t:
                    continue
                path_payload["links"].append({
                    "source": str(s),
                    "target": str(t),
                    "label": "PATH",
                    "isPath": True
                })
            return

        path_obj = row0.get("p")
        if not path_obj:
            return

        if not hasattr(path_obj, "nodes") or not hasattr(path_obj, "relationships"):
            return

        path_payload.setdefault("nodes", [])
        path_payload["links"] = []

        seen = set(n.get("id") for n in path_payload["nodes"] if isinstance(n, dict) and n.get("id"))

        for node in path_obj.nodes:
            nid = None
            try:
                nid = node.get("id")
            except Exception:
                nid = None
            if not nid:
                nid = getattr(node, "element_id", None) or getattr(node, "id", None)
            if not nid:
                continue

            nid = str(nid)
            if nid in seen:
                continue

            try:
                nname = node.get("name")
            except Exception:
                nname = None
            if not nname:
                nname = nid

            path_payload["nodes"].append({"id": nid, "name": nname})
            seen.add(nid)

        for rel in path_obj.relationships:
            try:
                s_node = getattr(rel, "start_node", None)
                t_node = getattr(rel, "end_node", None)
                s_id = (s_node.get("id") if s_node else None) or getattr(s_node, "element_id", None)
                t_id = (t_node.get("id") if t_node else None) or getattr(t_node, "element_id", None)
                if s_id and t_id:
                    path_payload["links"].append({
                        "source": str(s_id),
                        "target": str(t_id),
                        "label": "PATH",
                        "isPath": True
                    })
            except Exception:
                continue


    def _sanitize_answer(self, text: str) -> str:
        if not text:
            return text
        patterns = [
            r"(?is)^\s*RETURN\s+.*$",
            r"(?is)^\s*MATCH\s+.*$",
            r"(?is)^\s*CALL\s+.*$",
            r"(?is)cypher\s*query\s*:.*$",
            r"(?is)\bshortestPath\s*\(.*\)",
        ]
        out = text
        for p in patterns:
            out = re.sub(p, "", out, flags=re.MULTILINE)
        return out.strip()


    def _detect_path_mode(self, user_input: str) -> str:
        """shortest | longest | conditional | none"""
        q = (user_input or "").strip()

        # 최장
        if any(k in q for k in ["최장", "가장 긴", "최대", "최장거리", "가장 먼"]):
            return "longest"

        # 조건부 
        if any(k in q for k in ["거쳐", "거쳐서", "반드시", "포함", "must", "경유"]):
            return "conditional"

        # 최단/경로
        if any(k in q for k in ["최단", "경로", "흐름", "순서", "단계", "A부터", "부터", "까지"]):
            return "shortest"

        return "none"
    

    def _generate_path_cypher(self, question: str, mode: str) -> str:
        schema = self.graph.get_schema  # Neo4jGraph가 제공하는 schema string이거나 refresh_schema 후 내부 값
        # langchain_neo4j 버전에 따라 schema 접근이 다를 수 있어. 아래처럼 안전하게:
        try:
            schema_text = self.graph.schema
        except:
            schema_text = str(self.graph.get_schema) if hasattr(self.graph, "get_schema") else ""

        prompt = self.PATH_CYPHER_TEMPLATE.format(schema=schema_text, question=f"[mode={mode}] {question}")

        # OpenAI로 "Cypher만" 생성
        res = client.chat.completions.create(
            model=MODEL_CHAT,
            temperature=0,
            messages=[
                {"role": "system", "content": "Return ONLY Cypher. No markdown. No explanation."},
                {"role": "user", "content": prompt},
            ],
        )
        cypher = (res.choices[0].message.content or "").strip()
        return cypher
    

    def _validate_path_cypher(self, cypher: str) -> None:
        if not cypher:
            raise ValueError("Empty cypher")

        upper = cypher.upper()

        forbidden = ["CREATE", "MERGE", "DELETE", "SET ", "DROP", "LOAD CSV", "DBMS", "APOC.LOAD", "CALL DBMS"]
        if any(tok in upper for tok in forbidden):
            raise ValueError("Forbidden keyword in cypher")

        # 최소 조건: RETURN p
        if re.search(r"\bRETURN\s+p\b", cypher, re.IGNORECASE) is None:
            raise ValueError("Cypher must contain 'RETURN p'")

        ok = (
            "SHORTESTPATH" in upper
            or "NODES(P)" in upper
            or "RELATIONSHIPS(P)" in upper
            or "ALLSIMPLEPATHS" in upper
        )
        if not ok:
            raise ValueError("Not a path query")


    def _normalize_pid(self, x):
        """
        어떤 형태로 들어와도 최종적으로 'global_key' 문자열을 돌려주기.
        - dict: {"id": "..."} / {"global_key": "..."} / {"process_id": "..."} 등
        - str: "103906_..." 또는 "103906" 등
        """
        if not x:
            return None

        # dict 형태
        if isinstance(x, dict):
            return (x.get("id") or x.get("global_key") or x.get("pid") or x.get("process_id"))

        # 그냥 문자열
        return str(x).strip()


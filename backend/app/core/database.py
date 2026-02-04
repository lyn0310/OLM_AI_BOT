import os
import chromadb
from tqdm import tqdm
from langchain_neo4j import Neo4jGraph
from .config import DATASET_DIR, DATABASE_DIR, NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD
from .utils import get_embedding, chunk_text, extract_text_from_pptx, parse_markdown_structure

class DBBuilder:
    def __init__(self):
        try:
            self.graph = Neo4jGraph(url=NEO4J_URI, username=NEO4J_USERNAME, password=NEO4J_PASSWORD)
            print("[Neo4j] Connected successfully.")
        except Exception as e:
            print(f"[Neo4j] Connection Failed: {e}")
            self.graph = None

    def _build_common(self, source_folder, db_path, collection_name, file_ext, is_markdown=False, limit=None):
        print(f"\n[INFO] DB Build Start: {collection_name}")
        
        # 1. Vector DB Init
        os.makedirs(db_path, exist_ok=True)
        chroma_client = chromadb.PersistentClient(path=db_path)
        try: 
            chroma_client.delete_collection(name=collection_name)
        except: 
            pass
        collection = chroma_client.create_collection(name=collection_name)

        # 2. Graph DB Init
        if self.graph and is_markdown:
            print("[Neo4j] Clearing existing nodes...")
            self.graph.query("MATCH (n) DETACH DELETE n")

        # 3. File List 탐색
        file_list = []
        for root, dirs, files in os.walk(source_folder):
            for file in files:
                if file.endswith(file_ext):
                    category = os.path.basename(os.path.dirname(root))
                    file_list.append({
                        "path": os.path.join(root, file),
                        "category": category
                    })

        if limit: 
            file_list = file_list[:limit]

        if not file_list:
            print(f"[WARN] No {file_ext} files found in {source_folder}")
            return

        # 4. 전체 진행률
        total_chunks_count = 0
        for f_info in tqdm(file_list, desc=f"Files ({collection_name})", unit="file"):
            file_path = f_info["path"]
            category = f_info["category"]
            
            try:
                file_name = os.path.basename(file_path)
                ids, docs, embs, metas = [], [], [], []

                if is_markdown:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        text = f.read()
                    
                    parsed = parse_markdown_structure(text)
                    
                    if self.graph:
                        self._create_consolidated_graph(parsed, file_name, category)

                    for i, chunk in enumerate(parsed["chunks"]):
                        ids.append(f"{file_name}_{i}")
                        docs.append(chunk["text"])
                        embs.append(get_embedding(chunk["text"]))
                        metas.append({
                            "source": file_name,
                            "process_id": parsed["process_id"],
                            "global_key": parsed["global_key"],
                            "category": category,
                            "section": chunk["section"]
                        })
                        total_chunks_count += 1
                else:
                    text = extract_text_from_pptx(file_path)
                    chunks = chunk_text(text)
                    for i, chunk in enumerate(chunks):
                        if not chunk.strip(): continue
                        ids.append(f"{file_name}_{i}")
                        docs.append(chunk)
                        embs.append(get_embedding(chunk))
                        metas.append({"source": file_name, "category": "Manual"})
                        total_chunks_count += 1
                
                if ids:
                    collection.add(ids=ids, embeddings=embs, documents=docs, metadatas=metas)

            except Exception as e:
                tqdm.write(f"[ERROR] Error ({file_name}): {e}")

        print(f"\n[INFO] Build Complete! Total Chunks: {total_chunks_count}")

    def _create_consolidated_graph(self, parsed, file_name, category):
        pid = parsed["process_id"]
        pname = parsed["process_name"]
        gkey = parsed["global_key"]
        
        # 1. Root Node (Process)
        self.graph.query(
            """
            MERGE (p:Process {id: $gkey}) 
            SET p.name = $pname, 
                p.display_id = $pid, 
                p.source = $fname, 
                p.category = $category
            """,
            {
                "gkey": gkey, 
                "pname": pname, 
                "pid": pid, 
                "fname": file_name, 
                "category": category
            }
        )
        
        # 2. 상세 속성(Attribute) 연결
        if parsed["sections"]["상세속성"]:
            for attr in parsed["sections"]["상세속성"]:
                self.graph.query("""
                    MATCH (p:Process {id: $gkey})
                    MERGE (a:Attribute {id: $gkey + '_' + $key})
                    SET a.name = $key, a.value = $value
                    MERGE (p)-[:HAS_ATTRIBUTE]->(a)
                """, {"gkey": gkey, "key": attr["key"], "value": attr["value"]})

        # 3. 연관 항목(Relation) 연결
        if parsed["sections"]["연관항목"]:
            for rel in parsed["sections"]["연관항목"]:
                self.graph.query("""
                    MATCH (p:Process {id: $gkey})
                    MERGE (r:Process {id: $rel_gkey})
                    ON CREATE SET r.name = $rel_name
                    ON MATCH SET r.name = $rel_name 
                    MERGE (p)-[link:RELATED_TO]->(r)
                    SET link.label = $rel_label 
                """, {
                    "gkey": parsed["global_key"], 
                    "rel_gkey": rel["global_key"], 
                    "rel_name": rel["name"],
                    "rel_label": rel["rel_label"]
                })

    def build_process_db(self, limit=None):
        self._build_common(os.path.join(DATASET_DIR, "markdown"), os.path.join(DATABASE_DIR, "process_db"), "process_collection", ".md", True, limit)

    def build_project_db(self, limit=None):
        self._build_common(os.path.join(DATASET_DIR, "manual"), os.path.join(DATABASE_DIR, "project_db"), "project_collection", ".pptx", False, limit)

import streamlit as st
import chromadb
import pandas as pd
import os

# ==========================================
# 1. 설정 및 디자인
# ==========================================
st.set_page_config(layout="wide", page_title="OLM AI Knowledge Base Viewer")

st.title("📚 OLM AI 지식 베이스(DB) 뷰어")
st.markdown("---")

# [핵심 수정] 하드코딩 제거 및 경로 자동 감지
# 도커 환경변수가 있으면 사용하고, 없으면 현재 파일의 상위 폴더를 기준으로 삼습니다.
env_root = os.getenv("PROJECT_ROOT")
local_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DEFAULT_BASE_DIR = env_root if env_root else local_root

# ==========================================
# 2. 사이드바: 설정 및 DB 선택
# ==========================================
st.sidebar.header("⚙️ 설정")
base_dir = st.sidebar.text_input("프로젝트 루트 경로", value=DEFAULT_BASE_DIR)

db_option = st.sidebar.radio(
    "보고 싶은 DB를 선택하세요:",
    ("Process DB (Markdown 문서)", "Project DB (PPT 매뉴얼)")
)

# 선택에 따른 폴더 및 컬렉션 분기
if db_option == "Process DB (Markdown 문서)":
    db_folder = "process_db"
    collection_name = "process_collection"
else:
    db_folder = "project_db"
    collection_name = "project_collection"

# 최종 DB 경로 결합
db_path = os.path.join(base_dir, "database", db_folder)
st.sidebar.info(f"📍 현재 탐색 경로:\n{db_path}")

# ==========================================
# 3. ChromaDB 연결 및 조회
# ==========================================
try:
    if not os.path.exists(db_path):
        st.error(f"❌ DB 폴더를 찾을 수 없습니다: {db_path}")
        st.info("도커 볼륨 마운트 설정이나 백엔드 DB 생성 여부를 확인하세요.")
        st.stop()

    # ChromaDB 클라이언트 연결
    client = chromadb.PersistentClient(path=db_path)
    
    try:
        collection = client.get_collection(collection_name)
    except Exception:
        st.warning(f"⚠️ '{collection_name}' 컬렉션이 아직 생성되지 않았습니다.")
        st.stop()

    # 📊 상단 통계 표시
    count = collection.count()
    col1, col2, col3 = st.columns(3)
    col1.metric("선택된 DB", db_option)
    col2.metric("총 데이터 조각(Chunk)", f"{count}개")
    col3.metric("컬렉션 이름", collection_name)

    st.markdown("---")

    # 기능 탭 구성
    tab1, tab2 = st.tabs(["🔍 키워드 검색", "👀 전체 데이터 조회"])

    with tab1:
        st.subheader("데이터 검색")
        search_query = st.text_input("검색어를 입력하세요", placeholder="텍스트 매칭 방식 검색")

        if search_query:
            with st.spinner("검색 중..."):
                # ChromaDB 자체 필터링 기능 사용 (성능 최적화)
                results = collection.get(where_document={"$contains": search_query})
                
                if results['ids']:
                    matched_data = []
                    for i in range(len(results['ids'])):
                        meta = results['metadatas'][i]
                        matched_data.append({
                            "ID": results['ids'][i],
                            "파일명": meta.get('source', 'Unknown'),
                            "타입": meta.get('type', 'Unknown'),
                            "내용 (Chunk)": results['documents'][i]
                        })
                    st.success(f"✅ {len(matched_data)}건 발견")
                    st.dataframe(pd.DataFrame(matched_data), width="stretch", hide_index=True)
                else:
                    st.warning("검색 결과가 없습니다.")

    with tab2:
        st.subheader("전체 데이터 샘플링")
        limit_num = st.slider("조회 개수", 10, 500, 50)
        
        if st.button("데이터 불러오기"):
            data = collection.get(limit=limit_num)
            if data['ids']:
                df_all = pd.DataFrame({
                    "ID": data['ids'],
                    "파일명": [m.get('source', '-') for m in data['metadatas']],
                    "타입": [m.get('type', '-') for m in data['metadatas']],
                    "내용 (Chunk)": data['documents']
                })
                st.dataframe(df_all, width="stretch", hide_index=True)
            else:
                st.write("데이터가 비어있습니다.")

except Exception as e:
    st.error(f"DB 연결 중 오류 발생: {e}")
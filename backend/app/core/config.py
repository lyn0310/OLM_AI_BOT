import os

# --- 1. 경로 및 환경 설정 ---
IS_DOCKER = os.getenv("DATASET_DIR") is not None

if IS_DOCKER:
    print(f"[Config] Docker Environment Detected.")
    DATASET_DIR = os.getenv("DATASET_DIR", "/app/dataset")
    DATABASE_DIR = os.getenv("DATABASE_DIR", "/app/database")
    API_BASE_URL = os.getenv("API_BASE_URL")
else:
    print(f"[Config] Local Development Environment.")
    # 로컬 개발용 경로 계산
    CURRENT_FILE = os.path.abspath(__file__)
    # backend/app/core/config.py -> backend (3 levels up)
    # config.py is now in app/core, so we need to go up 3 levels to reach backend root?
    # Original: backend/app/config.py -> backend/ (2 levels up)
    # New: backend/app/core/config.py -> backend/ (3 levels up)
    
    # Let's re-verify the relative path.
    # __file__ = .../backend/app/core/config.py
    # dirname = .../backend/app/core
    # dirname = .../backend/app
    # dirname = .../backend
    # dirname = .../ (project root containing backend and dataset)
    
    # Original was:
    # CURRENT_FILE = os.path.abspath(__file__) # .../backend/app/config.py
    # PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(CURRENT_FILE)))
    # wait, dirname(config.py) = app. dirname(app) = backend. dirname(backend) = ROOT.
    # So 3 dirnames is correct for getting to d:\OLM_AI_BOT from app/config.py?
    # No, abspath includes filename.
    # 1. dirname -> .../backend/app
    # 2. dirname -> .../backend
    # 3. dirname -> .../OLM_AI_BOT (ROOT)
    
    # In new location: .../backend/app/core/config.py
    # 1. dirname -> .../backend/app/core
    # 2. dirname -> .../backend/app
    # 3. dirname -> .../backend
    # 4. dirname -> .../OLM_AI_BOT (ROOT)
    # So we need one more dirname call.
    
    CURRENT_FILE = os.path.abspath(__file__)
    PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(CURRENT_FILE))))
    DATASET_DIR = os.path.join(PROJECT_ROOT, "dataset")
    DATABASE_DIR = os.path.join(PROJECT_ROOT, "database")
    API_BASE_URL = "http://localhost:8050/api"

# --- 2. 모델 및 DB 설정 ---
API_KEY = os.getenv("OPENAI_API_KEY", "").strip()

MODEL_EMBEDDING = "text-embedding-3-small"
MODEL_CHAT = "gpt-4o-mini"

# Neo4j 설정
NEO4J_URI = os.getenv("NEO4J_URI", "bolt://localhost:7687")
NEO4J_USERNAME = os.getenv("NEO4J_USER", "neo4j")
NEO4J_PASSWORD = os.getenv("NEO4J_PASSWORD", "Sfolm2016!")

print(f" - API URL: {API_BASE_URL}")

import os
from ..core.config import DATASET_DIR

# ==========================================
# 1. 파일 위치 지도 (Global)
# ==========================================
file_path_map = {}

def index_files():
    """DATASET_DIR 하위의 모든 파일을 찾아 매핑"""
    global file_path_map
    file_path_map = {}
    
    print(f"\n[Debug] 파일 인덱싱 시작...")
    if not os.path.exists(DATASET_DIR):
        print(f"[Error] dataset 폴더를 찾을 수 없습니다: {DATASET_DIR}")
        return

    count = 0
    for root, dirs, files in os.walk(DATASET_DIR):
        for file in files:
            full_path = os.path.join(root, file)
            file_path_map[file] = full_path
            count += 1
            
    print(f"[System] 파일 인덱싱 완료! (총 {count}개 파일 발견)")

def get_file_path(filename: str):
    return file_path_map.get(filename)

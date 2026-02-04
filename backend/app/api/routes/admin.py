import os
from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse
from urllib.parse import unquote, quote

from ...core.database import DBBuilder
from ...services.file_service import index_files, get_file_path, file_path_map
from ..deps import get_bot

router = APIRouter()

@router.post("/build")
def build_db(target: str = "all", limit: int = None):
    try:
        builder = DBBuilder()
        
        if target in ["process", "all"]:
            builder.build_process_db(limit)
        if target in ["project", "all"]:
            builder.build_project_db(limit)
            
        index_files() # 파일맵 갱신
        
        # 봇 리로드 시도 (봇이 초기화되어 있다면)
        try:
            bot = get_bot()
            bot.reload_db()
        except:
            pass # 봇이 아직 없어도 빌드는 성공으로 처리
        
        return {"status": "success", "message": f"DB Build Complete ({target})"}
    except Exception as e:
        print(f"❌ [Error] DB 빌드 중 오류: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/download/{filename}")
def download_file(filename: str):
    decoded_filename = unquote(filename)
    file_path = get_file_path(decoded_filename)
    
    if not file_path or not os.path.exists(file_path):
        index_files() # 없으면 재검색
        file_path = get_file_path(decoded_filename)

    if file_path and os.path.exists(file_path):
        encoded_filename = quote(decoded_filename)
        return FileResponse(
            path=file_path,
            filename=decoded_filename, 
            media_type='application/octet-stream',
            headers={
                "Content-Disposition": f"attachment; filename*=UTF-8''{encoded_filename}"
            }
        )
    
    raise HTTPException(status_code=404, detail="파일을 찾을 수 없습니다.")

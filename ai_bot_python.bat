@echo off
chcp 65001 >nul
title OLM_AI_BOT

:: 1. 프로젝트 폴더로 이동
cd /d D:\OLM_AI_BOT
if errorlevel 1 (
    echo [ERROR] D:\OLM_AI_BOT 폴더를 찾을 수 없습니다.
    pause
    exit /b 1
)

:: .env 에서 포트 읽기 (안내 문구용)
for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
    if "%%a"=="MAIN_PORT" set MAIN_PORT=%%b
    if "%%a"=="BACKEND_PORT" set BACKEND_PORT=%%b
)

:: 2. YAML 문법 검사
echo [1/4] docker-compose.yml 검사
docker-compose config >nul 2>&1
if errorlevel 1 (
    echo [ERROR] YAML 오류가 있습니다. 상세 내용:
    docker-compose config
    pause
    exit /b 1
)
echo YAML OK
echo.

:: 3. 기존 컨테이너 정리
echo [2/4] 기존 컨테이너 종료
docker-compose down
echo.

:: 4. 빌드 및 실행
echo [3/4] 빌드 및 실행
docker-compose up -d --build
if errorlevel 1 (
    echo [ERROR] 기동 실패. 위 메시지를 확인하세요.
    pause
    exit /b 1
)
echo.

echo [4/4] 컨테이너 상태
docker-compose ps
echo.
echo ========================================================
echo  Frontend  : http://localhost:%MAIN_PORT%
echo  Backend   : http://localhost:%BACKEND_PORT%/docs
echo  DB Viewer : http://localhost:8501/viewer
echo  Neo4j     : http://localhost:7474
echo ========================================================
echo.
echo backend 로그 출력 (종료: Ctrl+C)
echo.

:: 5. backend 로그
docker logs -f olm-backend

pause
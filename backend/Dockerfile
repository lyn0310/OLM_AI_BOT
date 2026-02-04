FROM python:3.9-slim

WORKDIR /app

# 라이브러리 설치
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 소스 복사
COPY . .

# 8050 포트로 실행
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8050"]
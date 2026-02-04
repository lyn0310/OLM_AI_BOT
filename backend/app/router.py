from __future__ import annotations

import json
from typing import Optional

from openai import OpenAI

from app.router_models import RouteResult


class IntentRouter:
    """
    LLM 기반 Router.
    - output은 JSON only
    - 파싱 실패 시 keyword fallback
    """

    def __init__(self, client: OpenAI, model: str):
        self.client = client
        self.model = model

    def _build_router_prompt(self) -> str:
        # 너 도메인에 맞춰 "graph=관계/경로", "vector=정의서/요약"를 강하게 고정
        return """
        너는 질문을 분류하는 Router다. 반드시 JSON만 출력한다.

        [ROUTES]
        - graph: 노드/관계/흐름/경로/연결/단계/최단/최장/전체 프로세스 맵 탐색
        - vector: 특정 항목의 정의/설명/요약/담당자/시스템ID/규정/문서 내용 확인
        - both: 관계 탐색 + 상세 내용(담당자 포함) 확인이 모두 필요한 경우
        - chat: 인사/잡담/감정/일상/프로젝트와 무관한 단순 질문

        [HINTS]
        - "담당자가 누구야?", "~은 어디서 확인해?", "상세 내용 알려줘" -> vector 우선
        - "최장/최단/경로" -> graphModeHint 필수
        """

    def route(self, user_input: str) -> RouteResult:
        """
        user_input을 graph/vector/both/chat으로 분류한다.
        """
        user_input = (user_input or "").strip()
        if not user_input:
            return RouteResult(route="chat", confidence=0.9, reason="empty").normalized()

        try:
            messages = [
                {"role": "system", "content": self._build_router_prompt()},
                {"role": "user", "content": user_input},
            ]
            resp = self.client.chat.completions.create(
                model=self.model,
                messages=messages,
                temperature=0,
            )
            raw = (resp.choices[0].message.content or "").strip()

            raw = raw.replace("```json", "").replace("```", "").strip()

            data = json.loads(raw)
            r = RouteResult.from_dict(data).normalized()

            if r.confidence < 0.35:
                return RouteResult(route="chat", confidence=r.confidence, reason="low_confidence_fallback").normalized()

            return r

        except Exception:
            q = user_input
            if any(k in q for k in ["경로", "단계", "흐름", "연결", "관계", "최단", "최장"]):
                hint: Optional[str] = None
                if any(k in q for k in ["최장", "가장 긴", "가장 먼", "최대", "longest"]):
                    hint = "longest"
                elif any(k in q for k in ["최단", "shortest"]):
                    hint = "shortest"
                return RouteResult(route="graph", confidence=0.6, reason="keyword_fallback", graph_mode_hint=hint).normalized()

            if any(k in q for k in ["정의", "설명", "요약", "근거", "문서", "규정", "담당자", "누구", "어디"]):
                return RouteResult(route="vector", confidence=0.6, reason="keyword_fallback").normalized()

            return RouteResult(route="chat", confidence=0.5, reason="keyword_fallback").normalized()

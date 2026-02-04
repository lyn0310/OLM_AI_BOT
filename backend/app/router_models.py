# app/router_models.py
from __future__ import annotations

from dataclasses import dataclass
from typing import Optional, Dict, Any


@dataclass
class RouteResult:
    """
    Router 분류 결과를 담는 DTO
    route: graph | vector | both | chat
    """
    route: str
    confidence: float = 0.7
    reason: str = ""
    graph_mode_hint: Optional[str] = None  # "shortest" | "longest" | None

    @staticmethod
    def from_dict(d: Dict[str, Any]) -> "RouteResult":
        # graphModeHint 키 이름은 프롬프트 JSON과 맞춰둠
        return RouteResult(
            route=(d.get("route") or "chat").lower().strip(),
            confidence=float(d.get("confidence", 0.7)),
            reason=(d.get("reason") or "").strip()[:200],
            graph_mode_hint=(d.get("graphModeHint") or None),
        )

    def normalized(self) -> "RouteResult":
        """
        route/confidence 값 정규화 및 안정화.
        """
        self.route = (self.route or "chat").lower().strip()
        if self.route not in ("graph", "vector", "both", "chat"):
            self.route = "chat"

        # confidence 범위 클램프
        try:
            self.confidence = float(self.confidence)
        except Exception:
            self.confidence = 0.7
        self.confidence = max(0.0, min(1.0, self.confidence))

        if self.graph_mode_hint:
            self.graph_mode_hint = self.graph_mode_hint.lower().strip()
            if self.graph_mode_hint not in ("shortest", "longest"):
                self.graph_mode_hint = None

        return self

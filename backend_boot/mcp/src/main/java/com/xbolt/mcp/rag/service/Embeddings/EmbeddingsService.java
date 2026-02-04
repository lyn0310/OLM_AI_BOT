package com.xbolt.mcp.rag.service.Embeddings;

import org.springframework.stereotype.Service;

import java.util.List;

public interface EmbeddingsService {

    /**
     * vector DB 초기화
     * Chunked Dcoument -> Vector
     */
    List<float[]> embeddings(List<String> texts) throws Exception;

    /**
     * QA
     * 질문 -> 벡터로 변환
     */
    float[] embedding(String text) throws Exception;

}

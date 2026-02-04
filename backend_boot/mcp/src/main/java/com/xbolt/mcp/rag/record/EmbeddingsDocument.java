package com.xbolt.mcp.rag.record;

import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record EmbeddingsDocument(List<String> pageContent, List<Map<String, Object>> metadata, List<List<Float>> embeddings) {

    public static EmbeddingsDocument of(List<String> pageContents, List<Map<String, Object>> metadatas, List<List<Float>> embeddings){
        return new EmbeddingsDocument(pageContents, metadatas, embeddings);
    }

    public static EmbeddingsDocument of(List<Document> documents, List<List<Float>> embeddings){

        List<String> pageContents = documents.stream()
                .map(Document::pageContent)
                .collect(Collectors.toList());


        List<Map<String, Object>> metadatas= documents.stream()
                .map(Document::metadata)
                .collect(Collectors.toList());

        return new EmbeddingsDocument(pageContents, metadatas, embeddings);
    }


}

package com.xbolt.mcp.rag.service.Chunk;

import com.xbolt.mcp.rag.record.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CharChunkService implements ChunkService{

    @Value("${rag.chunk.size}")
    private int chunkSize;

    @Value("${rag.chunk.overlap}")
    private int chunkOverlap;

    public List<Document> chunkDocuments(List<Document> documents) {
        return documents.stream()
                .flatMap(document -> chunkSingleDocument(document).stream())
                .collect(Collectors.toList());
    }

    public List<Document> chunkSingleDocument(Document document) {
        List<Document> documents = new ArrayList<>();

        String content = document.pageContent();
        int start = 0;
        int chunkId = 0;

        while (start < content.length()) {
            int end = Math.min(start + chunkSize, content.length());
            String chunkText = content.substring(start, end);

            Map<String, Object> metadata = new HashMap<>(document.metadata());
            metadata.put("chunk_id", chunkId);
            metadata.put("parent_source", document.metadata().get("source"));


            documents.add(Document.of(chunkText, metadata));

            start += chunkSize - chunkOverlap;
            chunkId++;
        }

        return documents;
    }
}

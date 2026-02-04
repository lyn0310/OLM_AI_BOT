package com.xbolt.mcp.rag.service.Chunk;

import com.xbolt.mcp.rag.record.Document;

import java.util.List;

public interface ChunkService {

    List<Document> chunkDocuments(List<Document> documents) ;
    List<Document> chunkSingleDocument(Document document) ;
}

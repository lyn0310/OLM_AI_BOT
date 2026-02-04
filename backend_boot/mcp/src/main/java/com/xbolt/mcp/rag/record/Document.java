package com.xbolt.mcp.rag.record;

import java.util.Map;



public record Document(String pageContent, Map<String, Object> metadata) {

    public static Document of(String pageContent, Map<String, Object> metadata){
        return new Document(pageContent, metadata);
    }
}

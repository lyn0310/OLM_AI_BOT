package com.xbolt.mcp.rag.record;

import java.util.Map;

public record SearchResult(String content, float score) {

    public static SearchResult of(String content, float score){
        return new SearchResult(content, score);
    }
}

package com.xbolt.mcp.rag.dto;

import java.util.List;
import java.util.Map;

public record ParsedMarkdown(
        String globalKey,
        String processId,
        String processName,
        List<SectionChunk> chunks,
        List<RelationInfo> relations,
        List<Map<String, String>> attributes,
        String fullContent
) {}
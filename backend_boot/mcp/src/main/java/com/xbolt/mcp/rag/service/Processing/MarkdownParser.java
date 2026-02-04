package com.xbolt.mcp.rag.service.Processing;

import com.xbolt.mcp.rag.dto.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.*;

@Service
public class MarkdownParser {
    public ParsedMarkdown parse(String text) {
        // 1. Global Key
        Matcher headerMatcher = Pattern.compile("#\\s+항목\\s+정보:\\s+\\[([^\\]]+)\\]").matcher(text);
        String globalKey = headerMatcher.find() ? headerMatcher.group(1).trim() : "unknown";

        String[] parts = globalKey.split("_");
        String processId = parts.length > 0 ? parts[0] : "unknown";
        String processName = parts.length > 1 ? parts[1] : "unknown";

        List<SectionChunk> chunks = new ArrayList<>();
        List<RelationInfo> relations = new ArrayList<>();
        List<Map<String, String>> attributes = new ArrayList<>();

        // 2. 섹션 분할
        String[] sectionBlocks = text.split("##\\s+\\[");
        for (String block : sectionBlocks) {
            if (block.trim().isEmpty()) continue;

            String sectionHeader = block.split("\\]")[0];
            String content = block.substring(block.indexOf("]") + 1).trim();
            chunks.add(new SectionChunk(block, sectionHeader));

            // 3. 상세 속성
            if (sectionHeader.contains("상세속성")) {
                String[] attrBlocks = content.split("####\\s+");
                for (String ab : attrBlocks) {
                    String[] lines = ab.trim().split("\n");
                    if (lines.length > 1) {
                        attributes.add(Map.of("key", lines[0].trim(), "value", lines[1].trim()));
                    }
                }
            }
            // 4. 연관 항목
            else if (sectionHeader.contains("연관항목")) {
                String[] relBlocks = content.split("-\\s+연관\\s+No\\.");
                for (String rb : relBlocks) {
                    if (rb.trim().isEmpty()) continue;
                    Matcher idMatcher = Pattern.compile("Identity\\)\\*\\*:\\s*\\[([^\\]]+)\\]").matcher(rb);
                    if (idMatcher.find()) {
                        String relKey = idMatcher.group(1).trim();
                        String relName = relKey.contains("_") ? relKey.split("_")[1] : relKey;

                        // 관계 라벨
                        String label = "연관";
                        Matcher labelMatcher = Pattern.compile("-\\s+\\*\\*([^\\*]+)\\*\\*:\\s*([^\\n]+)").matcher(rb);
                        List<String> exclude = Arrays.asList("시스템 ID", "System ID", "항목 식별 정보", "Item Identity", "바로가기");
                        while (labelMatcher.find()) {
                            String k = labelMatcher.group(1);
                            if (exclude.stream().noneMatch(k::contains)) {
                                label = k.trim();
                                break;
                            }
                        }
                        relations.add(new RelationInfo(relName, relKey, label));
                    }
                }
            }
        }
        return new ParsedMarkdown(globalKey, processId, processName, chunks, relations, attributes, text);
    }
}
package com.xbolt.mcp.rag.service.Chunk;

import com.xbolt.mcp.rag.record.Document;
import com.xbolt.mcp.rag.service.Tokenizer.TokenizerService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.xbolt.mcp.common.util.CustomUtils.isEmpty;

@Primary
@Service
@RequiredArgsConstructor
public class TokenChunkService implements ChunkService{

    @Value("${rag.chunk.token.maxSize}")
    private int maxSize;

    @Value("${rag.chunk.token.overlap}")
    private int overlap;

    private final TokenizerService tokenizerService;

    public List<Document> chunkDocuments(List<Document> documents) {
        return documents.stream()
                .flatMap(document -> chunkSingleDocument(document).stream())
                .collect(Collectors.toList());
    }

    public List<Document> chunkSingleDocument(Document document) {
        List<Document> documents = new ArrayList<>();
        List<String> paragraphs = tokenizerService.splitToParagraphs(document.pageContent());

        List<String> buffer = new ArrayList<>();
        int tokenCount = 0;
        int chunkId = 0;

        for (String paragraph : paragraphs) {
            int paragraphTokens = tokenizerService.countTokens(paragraph);

            // maxSize 초과 시 청크 생성
            if (tokenCount + paragraphTokens > maxSize && !isEmpty(buffer)) {
                documents.add(createChunk(buffer, document.metadata(), chunkId++));

                if (overlap > 0) {
                    List<String> overlapBuffer = new ArrayList<>();
                    int overlapTokens = 0;
                    for (int i = buffer.size() - 1; i >= 0 && overlapTokens < overlap; i--) {
                        overlapBuffer.add(0, buffer.get(i));
                        overlapTokens += tokenizerService.countTokens(buffer.get(i));
                    }
                    buffer = overlapBuffer;
                    tokenCount = overlapBuffer.stream()
                            .mapToInt(tokenizerService::countTokens)
                            .sum();
                } else {
                    buffer.clear();
                    tokenCount = 0;
                }
            }

            if(!isEmpty(paragraph)){
                buffer.add(paragraph);
                tokenCount += paragraphTokens;
            }
        }

        if (!isEmpty(buffer)) {
            documents.add(createChunk(buffer, document.metadata(), chunkId));
        }
        return documents;
    }

    private Document createChunk(List<String> paragraphs, Map<String, Object> metadata, int chunkId) {
        String chunkText = String.join("\n\n", paragraphs);
        Map<String, Object> chunkMetadata = new HashMap<>(metadata);
        chunkMetadata.put("chunk_id", chunkId);
        chunkMetadata.put("parent_source", metadata.get("source"));
        chunkMetadata.put("paragraph_size", paragraphs.size());
        return Document.of(chunkText, chunkMetadata);
    }
}

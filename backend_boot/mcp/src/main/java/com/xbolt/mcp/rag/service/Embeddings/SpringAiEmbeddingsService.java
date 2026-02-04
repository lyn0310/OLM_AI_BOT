
package com.xbolt.mcp.rag.service.Embeddings;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Primary
@RequiredArgsConstructor
public class SpringAiEmbeddingsService implements EmbeddingsService {

    private final EmbeddingModel embeddingModel;

    @Override
    public List<float[]> embeddings(@NotNull List<String> texts) throws Exception {
        return embeddingModel.embed(texts);
    }

    @Override
    public float[] embedding(String text) throws Exception {
        return embeddingModel.embed(text);
    }

}

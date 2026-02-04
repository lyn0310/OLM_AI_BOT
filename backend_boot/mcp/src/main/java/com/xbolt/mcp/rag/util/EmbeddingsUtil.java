package com.xbolt.mcp.rag.util;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.xbolt.mcp.common.util.CustomUtils.isEmpty;

@Component
public class EmbeddingsUtil {

    public List<List<Float>> convertFloatArraysToFloat(List<float[]> embeddings){

        if(isEmpty(embeddings)) throw new NullPointerException("message is null");
        return embeddings.stream()
                .map(embedding -> convertFloatArrayToFloat(embedding))
                .collect(Collectors.toList());
    }

    public List<Float> convertFloatArrayToFloat(float[] embedding){

        if(isEmpty(embedding))  throw new NullPointerException("message is null");
        return IntStream.range(0, embedding.length)
                .mapToObj(i -> embedding[i])
                .collect(Collectors.toList());
    }
}

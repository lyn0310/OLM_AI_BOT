package com.xbolt.mcp.milvus.service;

import com.google.gson.JsonObject;
import com.xbolt.mcp.milvus.util.MilvusUtil;
import com.xbolt.mcp.rag.record.EmbeddingsDocument;
import io.milvus.param.dml.InsertParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MilvusService {

    private final MilvusUtil milvusUtil;

    @Value("${spring.ai.vectorstore.milvus.collection-name}")
    private String collectionName;

    public void insertDocuments(EmbeddingsDocument embeddingsDocument) throws Exception{

        if (embeddingsDocument.pageContent().size() != embeddingsDocument.metadata().size()
                || embeddingsDocument.pageContent().size() != embeddingsDocument.embeddings().size()) {
            throw new IllegalArgumentException("Column length mismatch");
        }

        List<InsertParam.Field> fields = new ArrayList<>();

        List<JsonObject> gsonJsonList = embeddingsDocument.metadata().stream()
                .map(map -> {
                    JsonObject json = new JsonObject();
                    map.forEach((k, v) -> json.addProperty(k, v.toString()));
                    return json;
                })
                .collect(Collectors.toList());

        fields.add(new InsertParam.Field("content", embeddingsDocument.pageContent()));

        fields.add(new InsertParam.Field("metadata", gsonJsonList));
        //fields.add(new InsertParam.Field("metadata", embeddingsDocument.metadata()));

        fields.add(new InsertParam.Field("embedding", embeddingsDocument.embeddings()));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields)
                .build();

        //insert & flush & create Index & load
        milvusUtil.insertDocumentInCollection(insertParam, collectionName, "embedding");

    }


    public void initCollection() throws Exception {
        milvusUtil.initCollection(collectionName);
    }
    public List<String> search(List<Float> vectorQuery) throws Exception{

        List<List<Float>> list = new ArrayList<>();
        list.add(vectorQuery);
        SearchResultsWrapper results = milvusUtil.search(list, collectionName);
        return milvusUtil.convertResultsToList(results);
    }


}

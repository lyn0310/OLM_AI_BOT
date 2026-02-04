package com.xbolt.mcp.milvus.util;

import io.milvus.client.MilvusClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.GetCollectionStatisticsResponse;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Component
@RequiredArgsConstructor
@Slf4j
public class MilvusUtil {

    private final ReentrantLock reentrantLock = new ReentrantLock();
    private final MilvusClient milvusClient;
    @Value("${rag.topK.size}")
    private long topK;
    @Value("${spring.ai.vectorstore.milvus.embedding-dimension}")
    private int dimension;

    public void initCollection(String name) throws Exception{

        reentrantLock.lock();
        try{
            if(isCollectionExists(name)) dropCollection(name);
            createCollection(name);
        }finally{
            reentrantLock.unlock();
        }
    }



    public void createCollection(String name) throws Exception {

        reentrantLock.lock();
        try {
            List<FieldType> fieldTypes = new ArrayList<>();

            fieldTypes.add(FieldType.newBuilder()
                    .withName("id")
                    .withDataType(DataType.Int64)
                    .withPrimaryKey(true)
                    .withAutoID(true)
                    .build()
            );
            fieldTypes.add(
                    FieldType.newBuilder()
                            .withName("content")
                            .withDataType(DataType.VarChar)
                            .withMaxLength(65535)
                            .build()
            );
            fieldTypes.add(
                    FieldType.newBuilder()
                            .withName("metadata")
                            .withDataType(DataType.JSON)
                            .build()
            );
            fieldTypes.add(
                    FieldType.newBuilder()
                            .withName("embedding")
                            .withDataType(DataType.FloatVector)
                            .withDimension(dimension)
                            .build()
            );


            milvusClient.createCollection(
                    CreateCollectionParam.newBuilder()
                            .withCollectionName(name)
                            .withSchema(CollectionSchemaParam.newBuilder()
                                    .withFieldTypes(fieldTypes)
                                    .build())
                            .build()
            );
        } finally {
            reentrantLock.unlock();
        }
    }

    public void insertDocumentInCollection(InsertParam insertParam, String name, String fieldName) throws Exception {

        milvusClient.insert(insertParam);
        flushDocuments(name);
        createIndex(name, fieldName);
        loadCollection(name);
    }

    public void dropCollection(String name) throws Exception {

        reentrantLock.lock();
        try {
            milvusClient.dropCollection(DropCollectionParam.newBuilder()
                    .withCollectionName(name)
                    .build());
        } finally {
            reentrantLock.unlock();
        }
    }

    public void loadCollection(String name) {
        milvusClient.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(name)
                        .build()
        );
    }

    public boolean isCollectionExists(String name) throws Exception {

        return milvusClient.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(name)
                .build()
        ).getData();
    }


    public void flushDocuments(String name) {

        reentrantLock.lock();
        try {
            milvusClient.flush(FlushParam.newBuilder()
                    .withCollectionNames(List.of(name))
                    .build());
        } finally {
            reentrantLock.unlock();
        }
    }

    public void createIndex(String name, String fieldName) {

        reentrantLock.lock();
        try {
            String indexName = fieldName + "_index";
            milvusClient.createIndex(
                    CreateIndexParam.newBuilder()
                            .withCollectionName(name)
                            .withFieldName(fieldName)
                            .withIndexName(indexName)
                            .withIndexType(IndexType.IVF_FLAT)
                            .withMetricType(MetricType.IP)
                            .withExtraParam("{\"nlist\":128}")
                            .build()
            );
        } catch (Exception e) {
            log.error("Index Already Exists : {}", e.getMessage());
        } finally {
            reentrantLock.unlock();
        }
    }

    public long countEntities(String name) throws Exception {
        GetCollectionStatisticsResponse stats = milvusClient.getCollectionStatistics(
                GetCollectionStatisticsParam.newBuilder()
                        .withCollectionName(name)
                        .build()
        ).getData();
        return stats.getStatsCount();
    }


    public SearchResultsWrapper search(List<List<Float>> queryVector, String name) throws Exception {

        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(name)
                .withMetricType(MetricType.IP)
                .withVectorFieldName("embedding")
                .withLimit(topK)
                .withFloatVectors(queryVector)
                .withOutFields(Arrays.asList("content"))
                .build();

        R<SearchResults> searchResults = milvusClient.search(searchParam);
        SearchResults results = searchResults.getData();

        SearchResultsWrapper searchResultsWrapper = new SearchResultsWrapper(results.getResults());

        return searchResultsWrapper;

    }

    public List<String> convertResultsToList(SearchResultsWrapper searchResultsWrapper) throws Exception {

        if (searchResultsWrapper == null) return Collections.emptyList();

        List<String> contents = new ArrayList<>();
        int queryCount = 1; // 여기서는 1개의 query vector만 검색했다고 가정
        // getIDScore(index) : 해당 query index 결과 리스트 반환

        List<SearchResultsWrapper.IDScore> idScores = searchResultsWrapper.getIDScore(0);

        if (idScores != null) {
            for (SearchResultsWrapper.IDScore idScore : idScores) {
                Object contentObj = idScore.getFieldValues().get("content");
                if (contentObj != null) {
                    contents.add(contentObj.toString());
                }
            }
        }

        return contents;
    }
}

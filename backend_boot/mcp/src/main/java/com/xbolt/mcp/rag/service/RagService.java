package com.xbolt.mcp.rag.service;


import com.xbolt.mcp.milvus.service.MilvusService;
import com.xbolt.mcp.rag.util.EmbeddingsUtil;
import com.xbolt.mcp.rag.record.Document;
import com.xbolt.mcp.rag.record.EmbeddingsDocument;
import com.xbolt.mcp.rag.service.Chunk.ChunkService;
import com.xbolt.mcp.rag.service.Embeddings.EmbeddingsService;
import com.xbolt.mcp.rag.service.Loader.FileLoaderService;
import com.xbolt.mcp.rag.service.Processing.ProcessingService;
import com.xbolt.mcp.rag.service.QaChain.QaChainService;
import com.xbolt.mcp.rag.service.Processing.MarkdownParser;
import com.xbolt.mcp.rag.service.Graph.Neo4jGraphService;
import com.xbolt.mcp.rag.dto.ParsedMarkdown;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.xbolt.mcp.common.util.CustomUtils.isEmpty;

@Service
@Slf4j
@RequiredArgsConstructor
public class RagService {

    private final Scheduler virtualThreadScheduler;

    @Autowired
    @Qualifier("webClientEmbeddingsService")
    private EmbeddingsService webClientEmbeddingsService;

    private final FileLoaderService fileLoaderService;
    private final ProcessingService processingService;
    private final EmbeddingsService embeddingsService;
    private final ChunkService chunkService;
    private final MilvusService milvusService;
    private final EmbeddingsUtil embeddingsUtil;
    private final QaChainService qaChainService;
    private final MarkdownParser markdownParser;
    private final Neo4jGraphService neo4jGraphService;

    /**
     * fileLoader -> Processing -> Chunk -> Embeddings -> Milvus
     *
     * @throws Exception
     */
    public Mono<Void> initVectorDbWithSpringAi() throws Exception {

        return Mono.fromRunnable(() -> {
                    try {
                        List<Document> documents = fileLoaderService.loadDocuments();
                        List<Document> processDocuments = processingService.processingDocuments(documents);
                        List<Document> chunkDocuments = chunkService.chunkDocuments(processDocuments);


                        List<String> chunkTexts = chunkDocuments.stream()
                                .map(Document::pageContent)
                                .toList();


                        List<float[]> floatArrays = webClientEmbeddingsService.embeddings(chunkTexts);

                        List<List<Float>> vectors = new ArrayList<>();
                        for (float[] arr : floatArrays) {
                            List<Float> list = new ArrayList<>(arr.length);
                            for (float f : arr) {
                                list.add(f);
                            }
                            vectors.add(list);
                        }

                        EmbeddingsDocument embeddingsDocument = EmbeddingsDocument.of(chunkDocuments, vectors);

                        milvusService.initCollection();
                        milvusService.insertDocuments(embeddingsDocument);
                    } catch (Exception e) {
                        log.error("RAG init error : {}", e.getMessage());
                        throw new RuntimeException(e);
                    }
                })
                .subscribeOn(virtualThreadScheduler)
                .doOnError(e -> log.error("RAG init failed : {}", e.getMessage()))
                .then();
    }

    /**
     * Embeddings -> ReRank -> qaChain
     *
     * @throws Exception
     */

    public Mono<String> search(String question) throws Exception {

        log.info("[Netty-Stage] API 호출 수신: {}", Thread.currentThread());

        if (isEmpty(question)) return Mono.error(new NullPointerException());

        return Mono.fromCallable(() -> {
                    log.info("[Virtual-Stage] 무거운 AI 작업 시작: {}", Thread.currentThread());

                    List<Float> vectorQuery = embeddingsUtil.convertFloatArrayToFloat(embeddingsService.embedding(question));
                    List<String> ragContexts = milvusService.search(vectorQuery);
                    String answer = qaChainService.chat(question, ragContexts);

                    log.info("[Virtual-Stage] 작업 완료: {}", Thread.currentThread());
                    return answer;
                })
                .subscribeOn(virtualThreadScheduler)
                .doOnSuccess(res -> log.info("[Netty-Stage] 결과 전달 준비: {}", Thread.currentThread()))
                .doOnError(e -> log.error("RAG search failed : {}", e.getMessage()));
    }










    /**
     * fileLoader -> Processing -> Chunk -> Embeddings -> Milvus
     *
     * @throws Exception
     */
    public void initVectorDbWithWebClient() throws Exception {

        List<Document> documents = fileLoaderService.loadDocuments();
        List<Document> processDocuments = processingService.processingDocuments(documents);
        List<Document> chunkDocuments = chunkService.chunkDocuments(processDocuments);


        List<String> chunkTexts = chunkDocuments.stream()
                .map(Document::pageContent)
                .toList();


        List<float[]> floatArrays = embeddingsService.embeddings(chunkTexts);

        List<List<Float>> vectors = new ArrayList<>();
        for (float[] arr : floatArrays) {
            List<Float> list = new ArrayList<>(arr.length);
            for (float f : arr) {
                list.add(f);
            }
            vectors.add(list);
        }

        EmbeddingsDocument embeddingsDocument = EmbeddingsDocument.of(chunkDocuments, vectors);

        //milvusService.initCollection();
        //milvusService.insertDocuments(embeddingsDocument);

    }

    public Mono<Void> buildGraphDb() {
        return Mono.defer(() -> {
            long t0 = System.currentTimeMillis();
            log.info("[Graph DB Build] Start");

            neo4jGraphService.createSchema();
            neo4jGraphService.clearAll();

            return Flux.defer(() -> Flux.fromIterable(wrapLoading()))
                    .subscribeOn(virtualThreadScheduler)
                    .parallel(16)
                    .runOn(virtualThreadScheduler)
                    .map(doc -> {
                        ParsedMarkdown parsed = markdownParser.parse(doc.pageContent());
                        String fileName = (String) doc.metadata().getOrDefault("source", "unknown_file");
                        return createUpsertMap(parsed, fileName);
                    })
                    .sequential()
                    .window(500)
                    .flatMap(batchFlux -> batchFlux.collectList()
                                    .flatMap(batch -> Mono.fromRunnable(() -> neo4jGraphService.upsertBatch(batch))
                                            .subscribeOn(virtualThreadScheduler))
                            , 12)
                    .then()
                    .doOnSuccess(v -> log.info("[Graph DB Build] End took={} ms", (System.currentTimeMillis() - t0)));
        }).subscribeOn(virtualThreadScheduler).then();
    }

    private List<Document> wrapLoading() {
        try {
            return fileLoaderService.loadDocuments();
        } catch (Exception e) {
            log.error("파일 로딩 중 예외 발생", e);
            return List.of();
        }
    }

    private Map<String, Object> createUpsertMap(ParsedMarkdown parsed, String fileName) {
        List<Map<String, String>> relations = new ArrayList<>(parsed.relations().size());
        for (var r : parsed.relations()) {
            relations.add(Map.of(
                    "rel_gkey", r.globalKey(),
                    "rel_name", r.name(),
                    "rel_label", r.label()
            ));
        }
        return Map.of(
                "gkey", parsed.globalKey(),
                "pname", parsed.processName(),
                "pid", parsed.processId(),
                "fname", fileName,
                "content", parsed.fullContent(),
                "attributes", parsed.attributes(),
                "relations", relations
        );
    }

}

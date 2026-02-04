package com.xbolt.mcp.rag.service.Embeddings;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class WebClientEmbeddingsService implements EmbeddingsService{

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.embedding.options.model}")
    private String embeddingModel;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.webClient.buffer.maxSize}")
    private int size;

    private WebClient webClient;

    @PostConstruct
    private void init(){
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " +apiKey)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(size))
                .build();
    }

    @Override
    public List<float[]> embeddings(List<String> texts) throws Exception {
        Map<String, Object> body = Map.of(
                "model", embeddingModel,
                "input", texts
        );

        List<float[]> embeddings = new ArrayList<>();

        try {
        Map response = this.webClient.post()
                .uri("/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(error -> HttpStatus.ACCEPTED.isError(), clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .map(RuntimeException::new)
                )
                .bodyToMono(Map.class)
                .block();


            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            for(Map<String, Object> item : data){
                List<Number> vector = (List<Number>) item.get("embedding");

                float[] arr = new float[vector.size()];
                for (int i = 0; i < vector.size(); i++) arr[i] = vector.get(i).floatValue();

                embeddings.add(arr);
            }

        } catch (WebClientResponseException e) {
            System.err.println("HTTP Status: " + e.getStatusCode());
            System.err.println("Response Body: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return embeddings;
    }

    @Override
    public float[] embedding(String text) throws Exception {
        return embeddings(List.of(text)).get(0);
    }
}

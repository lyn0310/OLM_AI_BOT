package com.xbolt.mcp.common.config;

import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusConfig {

    @Value("${spring.ai.vectorstore.milvus.client.host}")
    private String host;

    @Value("${spring.ai.vectorstore.milvus.client.port}")
    private int port;

    @Value("${spring.ai.vectorstore.milvus.client.username:}")
    private String username;

    @Value("${spring.ai.vectorstore.milvus.client.password:}")
    private String password;

    @Bean
    public MilvusClient milvusClient(){
        return new MilvusServiceClient(ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .build()
        );
    }

}

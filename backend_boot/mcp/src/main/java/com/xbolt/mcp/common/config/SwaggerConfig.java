package com.xbolt.mcp.common.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MCP API")
                        .description("Milvus 및 GraphDB 기반 RAG 서비스 API 문서")
                        .version("v0.0.1"));
    }
}

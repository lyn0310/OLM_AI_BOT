package com.xbolt.mcp.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

@Configuration
@EnableNeo4jRepositories(basePackages = "com.xbolt.mcp.rag.repository.neo4j")
public class Neo4jDataConfig {
}

package com.xbolt.mcp.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.xbolt.mcp.rag.repository.jpa")
public class JPAConfig {
}

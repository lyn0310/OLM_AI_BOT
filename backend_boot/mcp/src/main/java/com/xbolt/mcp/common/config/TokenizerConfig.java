package com.xbolt.mcp.common.config;

import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TokenizerConfig {

    @Bean
    public JTokkitTokenCountEstimator jTokkitTokenCountEstimator() {
        return new JTokkitTokenCountEstimator();
    }
}
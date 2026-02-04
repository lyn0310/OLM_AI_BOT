package com.xbolt.mcp.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Executors;

@Configuration
public class ThreadConfig {

    @Bean
    public Scheduler virtualThreadScheduler(){
        return Schedulers.fromExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }
}

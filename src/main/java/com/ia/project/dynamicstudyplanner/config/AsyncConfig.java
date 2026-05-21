package com.ia.project.dynamicstudyplanner.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous processing.
 * Configures a dedicated thread pool for heavy, CPU-bound genetic algorithm tasks,
 * preventing exhaustion of the main Tomcat servlet thread pool.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Creates a task executor tuned specifically for CPU-intensive work.
     * Core pool size is based on available CPU cores.
     */
    @Bean(name = "optimizerTaskExecutor")
    public Executor optimizerTaskExecutor() {
        int coreCount = Runtime.getRuntime().availableProcessors();
        log.info("Configuring optimizerTaskExecutor with {} cores", coreCount);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Since it's CPU bound, ideal thread count is near the core count.
        executor.setCorePoolSize(coreCount);
        executor.setMaxPoolSize(coreCount * 2);
        // Bounded queue: If the queue fills up, new tasks will be rejected immediately (fail-fast),
        // preventing memory exhaustion and endless waiting.
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("Optimizer-Async-");
        executor.initialize();
        return executor;
    }
}

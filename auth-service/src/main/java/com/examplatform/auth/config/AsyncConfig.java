package com.examplatform.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Dedicated thread pool for async audit logging.
 *
 * Audit log writes are @Async — they must never slow down login responses.
 * If the queue fills (extreme load), writes are dropped with a warning.
 * Authentication continues — audit logging is non-critical path.
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "auditLogExecutor")
    public Executor auditLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("audit-log-");
        executor.setRejectedExecutionHandler((runnable, pool) ->
                System.err.println("[WARN] Audit log task dropped — executor queue full")
        );
        executor.initialize();
        return executor;
    }
}

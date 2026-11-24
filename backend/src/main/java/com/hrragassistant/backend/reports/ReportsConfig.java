package com.hrragassistant.backend.reports;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Enables @Async for the analysis worker and defines its thread pool.
 *
 * Deliberately has no constructor dependencies: Spring Boot's JPA setup can
 * pick up a task executor bean while it builds the EntityManagerFactory, so a
 * repository injected here would form a startup cycle (EntityManagerFactory ->
 * this config -> repository -> EntityManagerFactory). The startup clean-up of
 * interrupted analyses lives in {@link InterruptedAnalysisRecovery} instead.
 */
@Configuration
@EnableAsync
public class ReportsConfig {

    /**
     * Analyses run on their own pool of two threads (app.reports.analysis-threads):
     * each one holds a whole report in memory, so a burst of uploads queues up
     * instead of running all at once.
     */
    @Bean(name = "reportAnalysisExecutor")
    public static ThreadPoolTaskExecutor reportAnalysisExecutor(@Value("${app.reports.analysis-threads:2}") int threads) {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("report-analysis-");
        executor.initialize();
        return executor;
    }
}

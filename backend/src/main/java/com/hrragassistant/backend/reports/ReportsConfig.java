package com.hrragassistant.backend.reports;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables @Async for the analysis worker, and on startup fails any analysis
 * a previous run left PENDING/PROCESSING (its background thread died with
 * the old process), so the UI shows "Re-analyze" instead of spinning forever.
 */
@Configuration
@EnableAsync
public class ReportsConfig {

    private static final Logger log = LoggerFactory.getLogger(ReportsConfig.class);

    private final FinancialReportAnalysisRepository analysisRepository;

    public ReportsConfig(FinancialReportAnalysisRepository analysisRepository) {
        this.analysisRepository = analysisRepository;
    }

    /**
     * Analyses run on their own pool of two threads (app.reports.analysis-threads):
     * each one holds a whole report in memory, so a burst of uploads queues up
     * instead of running all at once.
     */
    @org.springframework.context.annotation.Bean(name = "reportAnalysisExecutor")
    public org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor reportAnalysisExecutor(
            @org.springframework.beans.factory.annotation.Value("${app.reports.analysis-threads:2}") int threads) {
        var executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("report-analysis-");
        executor.initialize();
        return executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedAnalyses() {
        List<FinancialReportAnalysis> stuck = analysisRepository.findByStatusIn(
                List.of(ReportAnalysisStatus.PENDING, ReportAnalysisStatus.PROCESSING));
        for (FinancialReportAnalysis a : stuck) {
            a.markFailed("Interrupted by a server restart. Click Re-analyze to run it again.");
        }
        if (!stuck.isEmpty()) {
            analysisRepository.saveAll(stuck);
            log.info("Marked {} interrupted report analyses as failed", stuck.size());
        }
    }
}

package com.hrragassistant.backend.reports;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * On startup, fails any analysis a previous run left PENDING/PROCESSING (its
 * background thread died with the old process), so the UI shows "Re-analyze"
 * instead of spinning forever.
 */
@Component
public class InterruptedAnalysisRecovery {

    private static final Logger log = LoggerFactory.getLogger(InterruptedAnalysisRecovery.class);

    private final FinancialReportAnalysisRepository analysisRepository;

    public InterruptedAnalysisRecovery(FinancialReportAnalysisRepository analysisRepository) {
        this.analysisRepository = analysisRepository;
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

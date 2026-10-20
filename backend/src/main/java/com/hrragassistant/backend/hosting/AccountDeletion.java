package com.hrragassistant.backend.hosting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hrragassistant.backend.auth.UserRepository;
import com.hrragassistant.backend.ingestion.DocumentIngestionService;
import com.hrragassistant.backend.planning.HouseholdSnapshotRepository;
import com.hrragassistant.backend.reports.FinancialReportAnalysis;
import com.hrragassistant.backend.reports.FinancialReportAnalysisRepository;

/** Removes a person and everything they own (GDPR "right to erasure"). */
@Service
public class AccountDeletion {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletion.class);

    private final UserRepository users;
    private final FinancialReportAnalysisRepository analyses;
    private final DocumentIngestionService documents;
    private final HouseholdSnapshotRepository snapshots;

    public AccountDeletion(UserRepository users, FinancialReportAnalysisRepository analyses,
                           DocumentIngestionService documents, HouseholdSnapshotRepository snapshots) {
        this.users = users;
        this.analyses = analyses;
        this.documents = documents;
        this.snapshots = snapshots;
    }

    @Transactional
    public void delete(String username) {
        int reports = 0;
        for (FinancialReportAnalysis analysis : analyses.findByUploadedBy(username)) {
            analyses.delete(analysis);
            try {
                documents.delete(analysis.getDocumentId());
            } catch (RuntimeException e) {
                log.warn("Report {} of a deleted account was already gone: {}", analysis.getDocumentId(), e.getMessage());
            }
            reports++;
        }
        long checks = snapshots.deleteByUsername(username);
        users.findByUsername(username).ifPresent(users::delete);
        log.info("Deleted an account with {} reports and {} health checks", reports, checks);
    }
}

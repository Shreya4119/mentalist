package com.hrragassistant.backend.reports;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The AI fundamental analysis of one financial report (one row per report
 * document). The full, display-ready result -- extracted figures, computed
 * ratios and health score -- is stored as JSON so viewing a report never
 * calls the model again; only "Re-analyze" does.
 */
@Entity
@Table(name = "financial_report_analysis")
public class FinancialReportAnalysis {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID documentId;

    @Column(nullable = false)
    private String uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportAnalysisStatus status;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(columnDefinition = "text")
    private String resultJson;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(nullable = false)
    private Instant requestedAt;

    private Instant startedAt;

    private Instant completedAt;

    protected FinancialReportAnalysis() {
    }

    public FinancialReportAnalysis(UUID documentId, String uploadedBy, String provider, String model) {
        this.id = UUID.randomUUID();
        this.documentId = documentId;
        this.uploadedBy = uploadedBy;
        this.status = ReportAnalysisStatus.PENDING;
        this.provider = provider;
        this.model = model;
        this.requestedAt = Instant.now();
    }

    /** Resets for a fresh run, optionally with a different provider. */
    public void requeue(String provider, String model) {
        this.provider = provider;
        this.model = model;
        this.status = ReportAnalysisStatus.PENDING;
        this.errorMessage = null;
        this.requestedAt = Instant.now();
        this.startedAt = null;
        this.completedAt = null;
    }

    public void markProcessing() {
        this.status = ReportAnalysisStatus.PROCESSING;
        this.startedAt = Instant.now();
    }

    public void markCompleted(String resultJson) {
        this.status = ReportAnalysisStatus.COMPLETED;
        this.resultJson = resultJson;
        this.errorMessage = null;
        this.completedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = ReportAnalysisStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
    }

    public boolean isInFlight() {
        return status == ReportAnalysisStatus.PENDING || status == ReportAnalysisStatus.PROCESSING;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public ReportAnalysisStatus getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getResultJson() {
        return resultJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}

package com.hrragassistant.backend.reports;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialReportAnalysisRepository extends JpaRepository<FinancialReportAnalysis, UUID> {

    Optional<FinancialReportAnalysis> findByDocumentId(UUID documentId);

    List<FinancialReportAnalysis> findByDocumentIdIn(Collection<UUID> documentIds);

    List<FinancialReportAnalysis> findByStatusIn(Collection<ReportAnalysisStatus> statuses);

    List<FinancialReportAnalysis> findByUploadedBy(String uploadedBy);
}

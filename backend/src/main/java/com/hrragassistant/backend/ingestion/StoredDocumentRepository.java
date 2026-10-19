package com.hrragassistant.backend.ingestion;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredDocumentRepository extends JpaRepository<StoredDocument, UUID> {

    List<StoredDocument> findAllByOrderByUploadedAtDesc();

    List<StoredDocument> findAllByKindOrderByUploadedAtDesc(DocumentKind kind);

    List<StoredDocument> findAllByStatusIn(java.util.Collection<DocumentStatus> statuses);

    /** Lightweight listing: no file bytes are selected. */
    List<StoredDocumentSummary> findSummariesByKindOrderByUploadedAtDesc(DocumentKind kind);

    java.util.Optional<StoredDocumentSummary> findFirstBySha256AndKind(String sha256, DocumentKind kind);
}

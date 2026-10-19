package com.hrragassistant.backend.ingestion;

import java.time.Instant;
import java.util.UUID;

/**
 * A document row without its file bytes. Listing reports uses this so old
 * rows that still keep their bytes in Postgres are never loaded in bulk.
 */
public interface StoredDocumentSummary {

    UUID getId();

    String getFilename();

    DocumentStatus getStatus();

    DocumentKind getKind();

    Instant getUploadedAt();

    Long getSizeBytes();
}

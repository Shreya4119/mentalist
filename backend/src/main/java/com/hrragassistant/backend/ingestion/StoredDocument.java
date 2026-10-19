package com.hrragassistant.backend.ingestion;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * An uploaded file (today: a financial report) plus its indexing status.
 * The table keeps its original name, hr_document, so existing databases
 * carry on without a migration; rename it when Flyway is introduced.
 */
@Entity
@Table(name = "hr_document")
public class StoredDocument {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(32) default 'FINANCIAL_REPORT'")
    private DocumentKind kind = DocumentKind.FINANCIAL_REPORT;

    /**
     * Raw upload bytes for rows created before files moved to disk. New
     * uploads leave this null and use {@link #storagePath} instead, so large
     * reports never sit in Postgres or get loaded with every query.
     */
    @Column(columnDefinition = "bytea")
    private byte[] content;

    /** Path of the uploaded file inside the report file store (see ReportFileStore). */
    @Column(length = 512)
    private String storagePath;

    private Long sizeBytes;

    /** SHA-256 of the stored file, used to spot the same report uploaded twice. */
    @Column(length = 64)
    private String sha256;

    /** Detected media type, e.g. application/pdf; null for old rows (derived from the filename). */
    @Column(length = 100)
    private String mediaType;

    private Integer chunkCount;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(nullable = false)
    private Instant uploadedAt;

    private Instant indexedAt;

    protected StoredDocument() {
    }

    public StoredDocument(String filename, byte[] content, DocumentKind kind) {
        this.kind = kind;
        this.id = UUID.randomUUID();
        this.filename = filename;
        this.content = content;
        this.sizeBytes = content == null ? null : (long) content.length;
        this.status = DocumentStatus.QUEUED;
        this.uploadedAt = Instant.now();
    }

    /** A file-backed document: the bytes live in the report file store, not in this row. */
    public StoredDocument(String filename, String storagePath, long sizeBytes, String sha256, String mediaType, DocumentKind kind) {
        this.kind = kind;
        this.id = UUID.randomUUID();
        this.filename = filename;
        this.storagePath = storagePath;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.mediaType = mediaType;
        this.status = DocumentStatus.QUEUED;
        this.uploadedAt = Instant.now();
    }

    /** Back to the queue, e.g. after a restart interrupted processing. */
    public void markQueued() {
        this.status = DocumentStatus.QUEUED;
        this.errorMessage = null;
    }

    public void markProcessing() {
        this.status = DocumentStatus.PROCESSING;
    }

    public void markIndexed(int chunkCount) {
        this.status = DocumentStatus.INDEXED;
        this.chunkCount = chunkCount;
        this.indexedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = DocumentStatus.FAILED;
        this.errorMessage = errorMessage;
        this.indexedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    /** Only set for legacy rows; use DocumentIngestionService#openPath / readContent instead. */
    public byte[] getContent() {
        return content;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getMediaType() {
        return mediaType;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public DocumentKind getKind() {
        return kind == null ? DocumentKind.FINANCIAL_REPORT : kind;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public Instant getIndexedAt() {
        return indexedAt;
    }
}

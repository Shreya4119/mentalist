package com.hrragassistant.backend.ingestion;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "hr_document")
public class HrDocument {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    /**
     * Raw upload bytes, held here so the background SQS consumer has something
     * to process after the HTTP request completes. A production system would
     * use S3 for this; kept in Postgres to avoid a second AWS service in this
     * phase (see README).
     */
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    private Integer chunkCount;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @Column(nullable = false)
    private Instant uploadedAt;

    private Instant indexedAt;

    protected HrDocument() {
    }

    public HrDocument(String filename, byte[] content) {
        this.id = UUID.randomUUID();
        this.filename = filename;
        this.content = content;
        this.status = DocumentStatus.QUEUED;
        this.uploadedAt = Instant.now();
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

    public byte[] getContent() {
        return content;
    }

    public DocumentStatus getStatus() {
        return status;
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

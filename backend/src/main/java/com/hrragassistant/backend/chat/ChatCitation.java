package com.hrragassistant.backend.chat;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_citation")
public class ChatCitation {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID messageId;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private String filename;

    private Integer chunkIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String chunkText;

    protected ChatCitation() {
    }

    public ChatCitation(UUID messageId, UUID documentId, String filename, Integer chunkIndex, String chunkText) {
        this.id = UUID.randomUUID();
        this.messageId = messageId;
        this.documentId = documentId;
        this.filename = filename;
        this.chunkIndex = chunkIndex;
        this.chunkText = chunkText;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getFilename() {
        return filename;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public String getChunkText() {
        return chunkText;
    }
}

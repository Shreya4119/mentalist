package com.hrragassistant.backend.chat;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_feedback")
public class Feedback {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeedbackRating rating;

    @Column(nullable = false)
    private Instant createdAt;

    protected Feedback() {
    }

    public Feedback(UUID messageId, FeedbackRating rating) {
        this.id = UUID.randomUUID();
        this.messageId = messageId;
        this.rating = rating;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public FeedbackRating getRating() {
        return rating;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

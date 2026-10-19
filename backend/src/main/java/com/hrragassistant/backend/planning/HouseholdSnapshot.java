package com.hrragassistant.backend.planning;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One family financial health check: the numbers the user entered and the
 * computed result, both as JSON, so the yearly history can be shown without
 * recomputing (and old results keep the rules that applied at the time).
 */
@Entity
@Table(name = "household_snapshot")
public class HouseholdSnapshot {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 8)
    private String country;

    private Integer score;

    @Column(nullable = false)
    private String rating;

    @Column(nullable = false, columnDefinition = "text")
    private String inputJson;

    @Column(nullable = false, columnDefinition = "text")
    private String resultJson;

    protected HouseholdSnapshot() {
    }

    public HouseholdSnapshot(String username, String country, Integer score, String rating, String inputJson, String resultJson) {
        this.id = UUID.randomUUID();
        this.username = username;
        this.createdAt = Instant.now();
        this.country = country;
        this.score = score;
        this.rating = rating;
        this.inputJson = inputJson;
        this.resultJson = resultJson;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCountry() {
        return country;
    }

    public Integer getScore() {
        return score;
    }

    public String getRating() {
        return rating;
    }

    public String getInputJson() {
        return inputJson;
    }

    public String getResultJson() {
        return resultJson;
    }
}

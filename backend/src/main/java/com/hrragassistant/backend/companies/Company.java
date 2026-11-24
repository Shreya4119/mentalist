package com.hrragassistant.backend.companies;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A listed company whose official filings were imported, identified by its LEI. */
@Entity
@Table(name = "company")
public class Company {

    @Id
    @Column(length = 20)
    private String lei;

    @Column(nullable = false)
    private String name;

    @Column(length = 8)
    private String country;

    private Instant importedAt;

    @Column(length = 1000)
    private String importNote;

    protected Company() {
    }

    public Company(String lei, String name, String country) {
        this.lei = lei;
        this.name = name;
        this.country = country;
    }

    public String getLei() {
        return lei;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(Instant importedAt) {
        this.importedAt = importedAt;
    }

    public String getImportNote() {
        return importNote;
    }

    public void setImportNote(String importNote) {
        this.importNote = importNote;
    }
}

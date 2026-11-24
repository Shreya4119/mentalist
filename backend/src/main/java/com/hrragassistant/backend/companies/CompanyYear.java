package com.hrragassistant.backend.companies;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One financial year of a company, as read from an official filing. The
 * figures are stored as JSON (metric → amount and the IFRS concept it came
 * from), together with a link to the filing, so every number can be traced.
 */
@Entity
@Table(name = "company_year",
        uniqueConstraints = @UniqueConstraint(columnNames = {"lei", "periodEnd"}),
        indexes = @Index(columnList = "lei"))
public class CompanyYear {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 20)
    private String lei;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Column(length = 3)
    private String currency;

    @Column(nullable = false, columnDefinition = "text")
    private String valuesJson;

    /** The filing these figures were read from (its own year, or a later one's comparatives). */
    private LocalDate filingPeriodEnd;

    @Column(length = 1000)
    private String viewerUrl;

    private Instant importedAt;

    protected CompanyYear() {
    }

    public CompanyYear(String lei, LocalDate periodEnd, String currency, String valuesJson,
                       LocalDate filingPeriodEnd, String viewerUrl) {
        this.lei = lei;
        this.periodEnd = periodEnd;
        this.currency = currency;
        this.valuesJson = valuesJson;
        this.filingPeriodEnd = filingPeriodEnd;
        this.viewerUrl = viewerUrl;
        this.importedAt = Instant.now();
    }

    public String getLei() {
        return lei;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public String getCurrency() {
        return currency;
    }

    public String getValuesJson() {
        return valuesJson;
    }

    public LocalDate getFilingPeriodEnd() {
        return filingPeriodEnd;
    }

    public String getViewerUrl() {
        return viewerUrl;
    }
}

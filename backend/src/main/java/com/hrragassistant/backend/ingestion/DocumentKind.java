package com.hrragassistant.backend.ingestion;

/**
 * What a stored document is. Today every upload is a FINANCIAL_REPORT
 * (balance sheet, annual report) that gets an AI fundamental analysis and
 * can be chatted with. Kept as an enum so later kinds (e.g. broker
 * statements for the portfolio X-ray) slot in without a schema change.
 */
public enum DocumentKind {
    FINANCIAL_REPORT
}

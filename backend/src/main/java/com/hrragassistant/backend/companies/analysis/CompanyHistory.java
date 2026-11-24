package com.hrragassistant.backend.companies.analysis;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Everything the company page shows: the yearly figures and ratios, the
 * multi-year trends, three well-known warning-sign scores and plain-language
 * patterns. All numbers are computed in code from official filings.
 */
public record CompanyHistory(
        List<Year> years,
        Trends trends,
        List<Piotroski> piotroski,
        List<Altman> altman,
        List<Beneish> beneish,
        List<Finding> warnings,
        List<Finding> strengths) {

    /** figures: metric name → value; ratios: ratio key → value (null when inputs are missing). */
    public record Year(LocalDate periodEnd, String currency, Map<String, Double> figures, Map<String, Double> ratios) {
    }

    /** Compound annual growth rates over the whole span (null when not computable). */
    public record Trends(int yearsOfData, LocalDate firstYear, LocalDate lastYear,
                         Double revenueCagr, Double netIncomeCagr, Double operatingCashFlowCagr,
                         int revenueGrowthYears, int revenueDeclineYears) {
    }

    public enum Result { PASS, FAIL, UNKNOWN }

    public record Signal(String key, String label, Result result, String detail) {
    }

    /** Piotroski F-score: 9 yes/no checks of financial strength; higher is stronger. */
    public record Piotroski(LocalDate periodEnd, int score, int known, List<Signal> signals, String verdict) {
    }

    /** Altman Z'' (book-value version): bankruptcy-risk zone. */
    public record Altman(LocalDate periodEnd, Double z, String zone, Map<String, Double> parts, List<String> missing, String verdict) {
    }

    /** Beneish M-score: patterns that resemble companies that manipulated earnings. */
    public record Beneish(LocalDate periodEnd, Double m, boolean flagged, Map<String, Double> parts, List<String> missing, String verdict) {
    }

    /** A plain-language pattern with the numbers behind it. */
    public record Finding(String key, String title, String evidence, LocalDate periodEnd) {
    }
}

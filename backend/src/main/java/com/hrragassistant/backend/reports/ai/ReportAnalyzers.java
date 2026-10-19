package com.hrragassistant.backend.reports.ai;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The available AI providers and which one is the default
 * (app.reports.default-provider, "gemini" unless changed). If the default
 * has no API key but another provider does, that one is used instead.
 */
@Component
public class ReportAnalyzers {

    private final List<ReportAnalyzer> analyzers;
    private final String defaultProvider;

    public ReportAnalyzers(List<ReportAnalyzer> analyzers,
                           @Value("${app.reports.default-provider:gemini}") String defaultProvider) {
        this.analyzers = analyzers;
        this.defaultProvider = defaultProvider;
    }

    public List<ReportAnalyzer> all() {
        return analyzers;
    }

    public Optional<ReportAnalyzer> find(String id) {
        return analyzers.stream().filter(a -> a.id().equalsIgnoreCase(id)).findFirst();
    }

    /** The provider to use when the caller doesn't pick one. */
    public ReportAnalyzer defaultAnalyzer() {
        ReportAnalyzer preferred = find(defaultProvider).orElse(analyzers.get(0));
        if (preferred.configured()) {
            return preferred;
        }
        return analyzers.stream().filter(ReportAnalyzer::configured).findFirst().orElse(preferred);
    }

    public String defaultId() {
        return defaultAnalyzer().id();
    }

    /** Resolves a requested provider id (or null for the default) to a configured analyzer, or explains why not. */
    public ReportAnalyzer resolve(String requested) {
        ReportAnalyzer a = requested == null || requested.isBlank()
                ? defaultAnalyzer()
                : find(requested).orElseThrow(() -> new IllegalArgumentException("Unknown AI provider: " + requested));
        if (!a.configured()) {
            throw new IllegalArgumentException(a.label() + " is not configured. Add its API key to backend/.env and restart.");
        }
        return a;
    }
}

package com.hrragassistant.backend.reports;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrragassistant.backend.ingestion.DocumentIngestionService;
import com.hrragassistant.backend.ingestion.StoredDocument;
import com.hrragassistant.backend.ingestion.StoredDocumentRepository;
import com.hrragassistant.backend.reports.ai.ReportAnalyzer;
import com.hrragassistant.backend.reports.ai.ReportAnalyzers;
import com.hrragassistant.backend.reports.analysis.BalanceSheetExtraction;
import com.hrragassistant.backend.reports.analysis.ReportAnalysisView;
import com.hrragassistant.backend.reports.ai.RetryPolicy;
import com.hrragassistant.backend.reports.prep.ReportPreprocessor;
import org.springframework.web.client.RestClientResponseException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs one analysis in the background: the upload request returns at once
 * with a PENDING analysis and the UI polls until this finishes.
 *
 * A separate bean from ReportService on purpose: @Async only works through
 * the Spring proxy, so a call from inside the same class would silently run
 * synchronously and block the upload request.
 */
@Component
public class ReportAnalysisWorker {

    private static final Logger log = LoggerFactory.getLogger(ReportAnalysisWorker.class);

    static final ObjectMapper JSON = new ObjectMapper();

    private final FinancialReportAnalysisRepository analysisRepository;
    private final StoredDocumentRepository documentRepository;
    private final DocumentIngestionService ingestionService;
    private final ReportAnalyzers analyzers;
    private final MeterRegistry meterRegistry;
    private final ReportPreprocessor preprocessor;

    public ReportAnalysisWorker(FinancialReportAnalysisRepository analysisRepository,
                                StoredDocumentRepository documentRepository,
                                DocumentIngestionService ingestionService,
                                ReportAnalyzers analyzers,
                                MeterRegistry meterRegistry,
                                ReportPreprocessor preprocessor) {
        this.analysisRepository = analysisRepository;
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
        this.analyzers = analyzers;
        this.meterRegistry = meterRegistry;
        this.preprocessor = preprocessor;
    }

    /** Runs on its own small pool (ReportsConfig) so a burst of large uploads can't exhaust memory. */
    @Async("reportAnalysisExecutor")
    public void run(UUID analysisId) {
        FinancialReportAnalysis analysis = analysisRepository.findById(analysisId).orElse(null);
        if (analysis == null) {
            return;
        }
        analysis.markProcessing();
        analysisRepository.save(analysis);
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "failed";

        try {
            StoredDocument document = documentRepository.findById(analysis.getDocumentId())
                    .orElseThrow(() -> new IllegalStateException("The report file no longer exists"));
            ReportAnalyzer analyzer = analyzers.resolve(analysis.getProvider());
            ReportPreprocessor.Prepared prepared;
            try (DocumentIngestionService.FileHandle file = ingestionService.open(document)) {
                prepared = preprocessor.prepare(file.path(), document.getFilename(), document.getMediaType(),
                        analyzer.limits(), analyzer.label());
            }

            ReportAnalyzer.Result result = analyzeWithRetry(analyzer, prepared.source(), analysisId);
            List<String> notes = new ArrayList<>(prepared.notes());
            notes.addAll(result.processingNotes());
            int found = figuresFound(result.extraction());
            if (found < 4) {
                notes.add("Only " + found + " of the 15 standard figures were found. If this isn't an annual report, "
                        + "balance sheet or income statement, the score may not be meaningful.");
            }
            ReportAnalysisView view = ReportAnalysisView.assemble(
                    withNotes(new ReportAnalyzer.Result(result.extraction(), notes)), analyzer.id(), analyzer.model());
            analysis.markCompleted(JSON.writeValueAsString(view));
            analysisRepository.save(analysis);
            outcome = "completed";

            // Make the computed analysis citable in "Ask questions about this report".
            try {
                ingestionService.addSupplementalText(document.getId(), document.getFilename() + " (Mentalist analysis)",
                        view.toSearchableText(document.getFilename()));
            } catch (Exception e) {
                log.warn("Could not index analysis text for report {}: {}", document.getId(), e.getMessage());
            }
        } catch (Throwable t) {
            // Throwable, not Exception: a classpath problem surfaces as an Error, and the row must
            // still end FAILED rather than stay PROCESSING with the UI polling forever.
            log.error("Report analysis {} failed", analysisId, t);
            analysis.markFailed(t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
            analysisRepository.save(analysis);
        } finally {
            sample.stop(Timer.builder("reports.analysis.latency")
                    .tag("provider", String.valueOf(analysis.getProvider()))
                    .tag("outcome", outcome)
                    .register(meterRegistry));
        }
    }

    /** Retries rate limits, overloads, timeouts and malformed answers; gives up at once on anything else. */
    private ReportAnalyzer.Result analyzeWithRetry(ReportAnalyzer analyzer, ReportAnalyzer.Source source, UUID analysisId) throws Exception {
        for (int attempt = 1; ; attempt++) {
            try {
                return analyzer.analyze(source);
            } catch (Exception e) {
                boolean retry = attempt < RetryPolicy.MAX_ATTEMPTS && RetryPolicy.isTransient(e,
                        c -> c instanceof RestClientResponseException r ? r.getStatusCode().value() : -1);
                if (!retry) {
                    throw e;
                }
                long wait = RetryPolicy.backoffSeconds(attempt + 1);
                log.warn("Analysis {} attempt {} failed ({}); retrying in {}s", analysisId, attempt, e.getMessage(), wait);
                Thread.sleep(wait * 1000);
            }
        }
    }

    static int figuresFound(BalanceSheetExtraction x) {
        if (x == null || x.figures() == null) {
            return 0;
        }
        BalanceSheetExtraction.Figures f = x.figures();
        BalanceSheetExtraction.FigurePair[] all = {f.revenue(), f.operatingIncome(), f.netIncome(), f.interestExpense(),
                f.totalAssets(), f.currentAssets(), f.cashAndEquivalents(), f.inventory(), f.receivables(),
                f.totalLiabilities(), f.currentLiabilities(), f.totalDebt(), f.shareholdersEquity(),
                f.operatingCashFlow(), f.capitalExpenditure()};
        int n = 0;
        for (BalanceSheetExtraction.FigurePair p : all) {
            if (p != null && p.current() != null) n++;
        }
        return n;
    }

    /** Appends how-it-was-read notes (text fallback, truncation) to the model's own data-quality notes. */
    private static BalanceSheetExtraction withNotes(ReportAnalyzer.Result result) {
        BalanceSheetExtraction x = result.extraction();
        if (result.processingNotes().isEmpty()) {
            return x;
        }
        List<String> notes = new ArrayList<>(result.processingNotes());
        if (x.dataQualityNotes() != null) {
            notes.addAll(x.dataQualityNotes());
        }
        return new BalanceSheetExtraction(x.companyName(), x.reportingPeriod(), x.priorPeriod(), x.currency(), x.unit(),
                x.industry(), x.financialInstitution(), x.statementBasis(), x.figures(), x.summary(), x.highlights(),
                x.strengths(), x.redFlags(), x.visualInsights(), x.outlook(), notes);
    }
}

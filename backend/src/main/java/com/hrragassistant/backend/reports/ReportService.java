package com.hrragassistant.backend.reports;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrragassistant.backend.ingestion.DocumentIngestionService;
import com.hrragassistant.backend.ingestion.DocumentKind;
import com.hrragassistant.backend.ingestion.DocumentStatus;
import com.hrragassistant.backend.ingestion.StoredDocument;
import com.hrragassistant.backend.ingestion.StoredDocumentRepository;
import com.hrragassistant.backend.ingestion.StoredDocumentSummary;
import com.hrragassistant.backend.ingestion.storage.ReportFileStore;
import com.hrragassistant.backend.reports.intake.FileSniffer;
import com.hrragassistant.backend.reports.intake.UploadNames;
import com.hrragassistant.backend.reports.prep.ImageTools;
import com.hrragassistant.backend.reports.prep.PdfTools;
import com.hrragassistant.backend.reports.ai.ReportAnalyzer;
import com.hrragassistant.backend.reports.ai.ReportAnalyzers;
import com.hrragassistant.backend.reports.analysis.ReportAnalysisView;
import com.hrragassistant.backend.reports.analysis.ReportComparator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Financial reports: upload (stored as a FINANCIAL_REPORT document, indexed
 * for chat, and analysed by AI in the background), list, view, re-analyse,
 * delete and compare. Everyone signed in can view and upload; only the
 * uploader or an admin can re-analyse or delete a report.
 */
@Service
public class ReportService {

    /** Most page photos combined into one report in a single upload. */
    static final int MAX_PHOTOS = 40;

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final StoredDocumentRepository documentRepository;
    private final FinancialReportAnalysisRepository analysisRepository;
    private final DocumentIngestionService ingestionService;
    private final ReportAnalysisWorker worker;
    private final ReportAnalyzers analyzers;
    private final ReportFileStore fileStore;
    private final long maxUploadBytes;
    private final ReportAccess access;
    private final com.hrragassistant.backend.hosting.UsageLimits limits;

    public ReportService(StoredDocumentRepository documentRepository,
                         FinancialReportAnalysisRepository analysisRepository,
                         DocumentIngestionService ingestionService,
                         ReportAnalysisWorker worker,
                         ReportAnalyzers analyzers,
                         ReportFileStore fileStore,
                         ReportAccess access,
                         com.hrragassistant.backend.hosting.UsageLimits limits,
                         @org.springframework.beans.factory.annotation.Value("${app.reports.max-upload-mb:250}") long maxUploadMb) {
        this.documentRepository = documentRepository;
        this.analysisRepository = analysisRepository;
        this.ingestionService = ingestionService;
        this.worker = worker;
        this.analyzers = analyzers;
        this.fileStore = fileStore;
        this.maxUploadBytes = maxUploadMb * 1024 * 1024;
        this.access = access;
        this.limits = limits;
    }

    /** The same file was uploaded before; the UI opens the existing report instead. */
    public static class DuplicateReport extends RuntimeException {
        private final UUID existingId;

        DuplicateReport(UUID existingId, String filename) {
            super(filename + " was already uploaded; opening the existing analysis.");
            this.existingId = existingId;
        }

        public UUID existingId() {
            return existingId;
        }
    }

    // ------------------------------------------------------------------ DTOs

    public record ProviderInfo(String id, String label, String model, boolean configured, boolean isDefault) {
    }

    public record ReportSummary(UUID id, String filename, Instant uploadedAt, String uploadedBy,
                                DocumentStatus documentStatus, boolean chatReady,
                                ReportAnalysisStatus analysisStatus, String provider, String model,
                                String companyName, String reportingPeriod, Integer healthScore, String healthRating,
                                String errorMessage, Instant analysisRequestedAt, Instant analysisCompletedAt,
                                boolean shared) {
    }

    public record ReportDetail(ReportSummary report, ReportAnalysisView analysis, boolean canManage) {
    }

    // ------------------------------------------------------------------ queries

    public List<ProviderInfo> providers() {
        String def = analyzers.defaultId();
        return analyzers.all().stream()
                .map(a -> new ProviderInfo(a.id(), a.label(), a.model(), a.configured(), a.id().equals(def)))
                .toList();
    }

    /** The person's own reports plus the shared sample reports (admins see all). */
    public List<ReportSummary> list(String username, boolean admin) {
        // Projection: file bytes of old rows are never loaded just to show the list.
        List<StoredDocumentSummary> docs = documentRepository.findSummariesByKindOrderByUploadedAtDesc(DocumentKind.FINANCIAL_REPORT);
        Map<UUID, FinancialReportAnalysis> analyses = analysisRepository
                .findByDocumentIdIn(docs.stream().map(StoredDocumentSummary::getId).toList()).stream()
                .collect(Collectors.toMap(FinancialReportAnalysis::getDocumentId, Function.identity()));
        Set<String> shared = access.sharedOwners();
        return docs.stream()
                .filter(d -> ReportAccess.canView(analyses.get(d.getId()), username, admin, shared))
                .map(d -> summary(d.getId(), d.getFilename(), d.getUploadedAt(), d.getStatus(),
                        analyses.get(d.getId()), view(analyses.get(d.getId())), ReportAccess.isShared(analyses.get(d.getId()), shared)))
                .toList();
    }

    public ReportDetail get(UUID id, String username, boolean admin) {
        StoredDocument doc = report(id);
        FinancialReportAnalysis analysis = analysisRepository.findByDocumentId(id).orElse(null);
        Set<String> shared = access.sharedOwners();
        if (!ReportAccess.canView(analysis, username, admin, shared)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found: " + id);
        }
        ReportAnalysisView view = view(analysis);
        return new ReportDetail(summary(doc, analysis, view, ReportAccess.isShared(analysis, shared)), view, canManage(analysis, username, admin));
    }

    // ------------------------------------------------------------------ commands

    /**
     * One report file, or several page photos that are combined into one PDF.
     * Every file is checked by its contents (not just its name), large files
     * are streamed to disk, and a file uploaded before opens the existing
     * analysis instead of being analysed twice.
     */
    public ReportSummary upload(List<MultipartFile> files, String provider, String username, boolean admin) throws IOException {
        List<MultipartFile> parts = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (parts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is empty");
        }
        if (parts.size() > MAX_PHOTOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload at most " + MAX_PHOTOS + " page photos at once");
        }
        long total = parts.stream().mapToLong(MultipartFile::getSize).sum();
        if (total > maxUploadBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Reports are limited to " + (maxUploadBytes / 1024 / 1024) + " MB. Upload just the financial statements pages, or compress the PDF.");
        }
        ReportAnalyzer analyzer = resolveProvider(provider);

        List<FileSniffer.Kind> kinds = new java.util.ArrayList<>();
        for (MultipartFile f : parts) {
            kinds.add(check(f));
        }

        String filename;
        String mediaType;
        ReportFileStore.StoredFile stored;
        if (parts.size() > 1) {
            if (!kinds.stream().allMatch(FileSniffer.Kind::isConvertibleImage)) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "Several files can only be combined when they are all page photos (JPG, PNG, TIFF, GIF or BMP). Upload a PDF or spreadsheet on its own.");
            }
            filename = UploadNames.stem(UploadNames.sanitize(parts.get(0).getOriginalFilename())) + " (" + parts.size() + " pages).pdf";
            stored = fileStore.save(photosToPdf(parts), "pdf");
            mediaType = "application/pdf";
        } else {
            MultipartFile f = parts.get(0);
            FileSniffer.Kind kind = kinds.get(0);
            filename = UploadNames.sanitize(f.getOriginalFilename());
            if (kind == FileSniffer.Kind.TIFF || kind == FileSniffer.Kind.GIF || kind == FileSniffer.Kind.BMP) {
                // The AI providers don't read these formats; one PDF page per image works everywhere (multi-page TIFF scans too).
                stored = fileStore.save(photosToPdf(parts), "pdf");
                filename = UploadNames.stem(filename) + ".pdf";
                mediaType = "application/pdf";
            } else {
                try (var in = f.getInputStream()) {
                    stored = fileStore.save(in, UploadNames.extension(filename));
                }
                mediaType = kind.isProviderImage() || kind == FileSniffer.Kind.PDF ? kind.mediaType() : null;
            }
        }

        // Only reports this person can see count as duplicates: someone else's private
        // copy of the same file must not be revealed (or opened) through an upload.
        Set<String> shared = access.sharedOwners();
        var existing = documentRepository.findBySha256AndKind(stored.sha256(), DocumentKind.FINANCIAL_REPORT).stream()
                .filter(d -> ReportAccess.canView(analysisRepository.findByDocumentId(d.getId()).orElse(null), username, admin, shared))
                .findFirst();
        if (existing.isPresent()) {
            fileStore.delete(stored.relativePath());
            throw new DuplicateReport(existing.get().getId(), existing.get().getFilename());
        }
        try {
            limits.acquireAnalysis(username, admin);
        } catch (RuntimeException e) {
            fileStore.delete(stored.relativePath());
            throw e;
        }

        StoredDocument doc = ingestionService.enqueueStored(filename, stored, mediaType, DocumentKind.FINANCIAL_REPORT);
        FinancialReportAnalysis analysis = analysisRepository.save(
                new FinancialReportAnalysis(doc.getId(), username, analyzer.id(), analyzer.model()));
        worker.run(analysis.getId());
        return summary(doc, analysis, null, admin);
    }

    /** Validates one part by name and contents; returns what it really is. */
    private FileSniffer.Kind check(MultipartFile f) throws IOException {
        String name = UploadNames.sanitize(f.getOriginalFilename());
        String ext = UploadNames.extension(name);
        if (ext.equals("heic") || ext.equals("heif")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, FileSniffer.mismatch(ext, FileSniffer.Kind.HEIC));
        }
        if (!FileSniffer.ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported file type" + (ext.isEmpty() ? "" : " (." + ext + ")")
                            + ". Upload a PDF, an image (PNG, JPG, WEBP, TIFF), a spreadsheet (XLSX, XLS, CSV), a Word file or a text file.");
        }
        byte[] head;
        try (var in = f.getInputStream()) {
            head = in.readNBytes(4096);
        }
        FileSniffer.Kind kind = FileSniffer.detect(head);
        String problem = FileSniffer.mismatch(ext, kind);
        if (problem != null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, name + ": " + problem);
        }
        return kind;
    }

    private static byte[] photosToPdf(List<MultipartFile> parts) throws IOException {
        List<java.awt.image.BufferedImage> pages = new java.util.ArrayList<>();
        for (MultipartFile f : parts) {
            try {
                pages.addAll(ImageTools.readPages(f.getBytes()));
            } catch (IOException e) {
                throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        UploadNames.sanitize(f.getOriginalFilename()) + " could not be read as an image. It may be damaged.");
            }
        }
        return PdfTools.imagesToPdf(pages, 0.8f);
    }

    public ReportSummary reanalyze(UUID id, String provider, String username, boolean admin) {
        StoredDocument doc = report(id);
        FinancialReportAnalysis analysis = analysisRepository.findByDocumentId(id)
                .orElseGet(() -> new FinancialReportAnalysis(id, username, analyzers.defaultId(), analyzers.defaultAnalyzer().model()));
        requireManage(analysis, username, admin);
        if (analysis.isInFlight()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This report is already being analysed");
        }
        ReportAnalyzer analyzer = resolveProvider(provider);
        limits.acquireAnalysis(username, admin);
        analysis.requeue(analyzer.id(), analyzer.model());
        analysisRepository.save(analysis);
        worker.run(analysis.getId());
        return summary(doc, analysis, null, ReportAccess.isShared(analysis, access.sharedOwners()));
    }

    public void delete(UUID id, String username, boolean admin) {
        report(id);
        FinancialReportAnalysis analysis = analysisRepository.findByDocumentId(id).orElse(null);
        requireManage(analysis, username, admin);
        if (analysis != null) {
            analysisRepository.delete(analysis);
        }
        ingestionService.delete(id);
    }

    public ReportComparator.Comparison compare(UUID a, UUID b, String username, boolean admin) {
        if (a.equals(b)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pick two different reports to compare");
        }
        StoredDocument docA = report(a);
        StoredDocument docB = report(b);
        access.requireView(a, username, admin);
        access.requireView(b, username, admin);
        ReportAnalysisView viewA = completedView(a, docA);
        ReportAnalysisView viewB = completedView(b, docB);
        return ReportComparator.compare(a.toString(), docA.getFilename(), viewA, b.toString(), docB.getFilename(), viewB);
    }

    // ------------------------------------------------------------------ helpers

    private ReportAnalyzer resolveProvider(String provider) {
        try {
            return analyzers.resolve(provider);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private StoredDocument report(UUID id) {
        return documentRepository.findById(id)
                .filter(d -> d.getKind() == DocumentKind.FINANCIAL_REPORT)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found: " + id));
    }

    private ReportAnalysisView completedView(UUID id, StoredDocument doc) {
        ReportAnalysisView v = view(analysisRepository.findByDocumentId(id).orElse(null));
        if (v == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, doc.getFilename() + " has not finished analysis yet");
        }
        return v;
    }

    private static ReportAnalysisView view(FinancialReportAnalysis analysis) {
        if (analysis == null || analysis.getStatus() != ReportAnalysisStatus.COMPLETED || analysis.getResultJson() == null) {
            return null;
        }
        try {
            return JSON.readValue(analysis.getResultJson(), ReportAnalysisView.class);
        } catch (IOException e) {
            throw new IllegalStateException("Stored analysis could not be read; re-analyse the report", e);
        }
    }

    private static boolean canManage(FinancialReportAnalysis analysis, String username, boolean admin) {
        return admin || (analysis != null && analysis.getUploadedBy().equals(username));
    }

    private static void requireManage(FinancialReportAnalysis analysis, String username, boolean admin) {
        if (!canManage(analysis, username, admin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the uploader or an admin can change this report");
        }
    }

    private static ReportSummary summary(StoredDocument d, FinancialReportAnalysis a, ReportAnalysisView v, boolean shared) {
        return summary(d.getId(), d.getFilename(), d.getUploadedAt(), d.getStatus(), a, v, shared);
    }

    private static ReportSummary summary(UUID id, String filename, Instant uploadedAt, DocumentStatus status,
                                         FinancialReportAnalysis a, ReportAnalysisView v, boolean shared) {
        return new ReportSummary(id, filename, uploadedAt,
                a == null ? null : a.getUploadedBy(),
                status,
                // Chat-ready once the full text is indexed OR the analysis is done (its digest is indexed).
                status == DocumentStatus.INDEXED || (a != null && a.getStatus() == ReportAnalysisStatus.COMPLETED),
                a == null ? null : a.getStatus(), a == null ? null : a.getProvider(), a == null ? null : a.getModel(),
                v == null ? null : v.extraction().companyName(),
                v == null ? null : v.extraction().reportingPeriod(),
                v == null ? null : v.health().score(),
                v == null ? null : v.health().rating(),
                a == null ? null : a.getErrorMessage(),
                a == null ? null : a.getRequestedAt(),
                a == null ? null : a.getCompletedAt(),
                shared);
    }
}

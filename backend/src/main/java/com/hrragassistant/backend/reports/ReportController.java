package com.hrragassistant.backend.reports;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import com.hrragassistant.backend.reports.analysis.ReportComparator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Financial reports API. All endpoints need a signed-in user (any role);
 * re-analyse and delete are further limited to the uploader or an admin
 * inside ReportService.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/providers")
    public List<ReportService.ProviderInfo> providers() {
        return reportService.providers();
    }

    @GetMapping
    public List<ReportService.ReportSummary> list(Authentication auth) {
        return reportService.list(auth.getName(), isAdmin(auth));
    }

    @PostMapping
    public ResponseEntity<ReportService.ReportSummary> upload(@RequestParam(value = "file", required = false) List<MultipartFile> files,
                                                              @RequestParam(value = "provider", required = false) String provider,
                                                              Authentication auth) throws IOException {
        // "file" may repeat: several page photos are combined into one report.
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(reportService.upload(files, provider, auth.getName(), isAdmin(auth)));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(ReportService.DuplicateReport.class)
    public ResponseEntity<java.util.Map<String, Object>> duplicate(ReportService.DuplicateReport e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(java.util.Map.of("message", e.getMessage(), "existingId", e.existingId().toString()));
    }

    @GetMapping("/compare")
    public ReportComparator.Comparison compare(@RequestParam("a") UUID a, @RequestParam("b") UUID b, Authentication auth) {
        return reportService.compare(a, b, auth.getName(), isAdmin(auth));
    }

    @GetMapping("/{id}")
    public ReportService.ReportDetail get(@PathVariable UUID id, Authentication auth) {
        return reportService.get(id, auth.getName(), isAdmin(auth));
    }

    @PostMapping("/{id}/analysis")
    public ResponseEntity<ReportService.ReportSummary> reanalyze(@PathVariable UUID id,
                                                                 @RequestParam(value = "provider", required = false) String provider,
                                                                 Authentication auth) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(reportService.reanalyze(id, provider, auth.getName(), isAdmin(auth)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
        reportService.delete(id, auth.getName(), isAdmin(auth));
        return ResponseEntity.noContent().build();
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}

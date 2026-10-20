package com.hrragassistant.backend.reports;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.hrragassistant.backend.auth.Role;
import com.hrragassistant.backend.auth.User;
import com.hrragassistant.backend.auth.UserRepository;

/**
 * Who may see a report: the person who uploaded it, and everyone for reports
 * an admin uploaded (the shared sample reports). Admins see everything.
 * A report someone isn't allowed to see answers 404, as if it didn't exist.
 */
@Component
public class ReportAccess {

    private final UserRepository users;
    private final FinancialReportAnalysisRepository analyses;

    public ReportAccess(UserRepository users, FinancialReportAnalysisRepository analyses) {
        this.users = users;
        this.analyses = analyses;
    }

    /** Usernames whose uploads are shared with everyone (the admins). */
    public Set<String> sharedOwners() {
        return users.findByRole(Role.ADMIN).stream().map(User::getUsername).collect(Collectors.toSet());
    }

    public static boolean canView(FinancialReportAnalysis analysis, String username, boolean admin, Set<String> sharedOwners) {
        if (admin) {
            return true;
        }
        if (analysis == null) {
            return false;
        }
        return analysis.getUploadedBy().equals(username) || sharedOwners.contains(analysis.getUploadedBy());
    }

    public static boolean isShared(FinancialReportAnalysis analysis, Set<String> sharedOwners) {
        return analysis != null && sharedOwners.contains(analysis.getUploadedBy());
    }

    public void requireView(UUID documentId, String username, boolean admin) {
        if (admin) {
            return;
        }
        FinancialReportAnalysis analysis = analyses.findByDocumentId(documentId).orElse(null);
        if (!canView(analysis, username, false, sharedOwners())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found: " + documentId);
        }
    }
}

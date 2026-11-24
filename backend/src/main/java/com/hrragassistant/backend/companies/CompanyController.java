package com.hrragassistant.backend.companies;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Listed companies: search the official EU filings, import them, and see the multi-year analysis. */
@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService service;

    public CompanyController(CompanyService service) {
        this.service = service;
    }

    @GetMapping
    public List<CompanyService.CompanySummary> list() {
        return service.list();
    }

    @GetMapping("/search")
    public List<CompanyService.SearchHit> search(@RequestParam("q") String q) {
        return service.search(q);
    }

    @GetMapping("/countries")
    public List<CompanyService.Country> countries() {
        return service.countryList();
    }

    @GetMapping("/directory")
    public List<CompanyService.DirectoryItem> directory(@RequestParam("country") String country) {
        return service.directory(country);
    }

    @GetMapping("/{lei}")
    public CompanyService.CompanyView get(@PathVariable String lei) {
        return service.get(lei);
    }

    @PostMapping("/{lei}/import")
    public CompanyService.CompanyView importCompany(@PathVariable String lei,
                                                    @RequestParam(value = "refresh", defaultValue = "false") boolean refresh,
                                                    Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return service.importCompany(lei, auth.getName(), admin, refresh);
    }

    /** Plain {message} bodies, which the frontend shows as they are. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> error(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "Something went wrong" : e.getReason()));
    }
}

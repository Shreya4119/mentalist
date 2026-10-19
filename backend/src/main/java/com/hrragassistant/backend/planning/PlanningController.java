package com.hrragassistant.backend.planning;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hrragassistant.backend.planning.analysis.HouseholdInput;

/**
 * Family financial health check. Every signed-in user has their own private
 * history; nobody (admins included) can read another user's numbers.
 */
@RestController
@RequestMapping("/api/planning/health-check")
public class PlanningController {

    private final PlanningService planningService;

    public PlanningController(PlanningService planningService) {
        this.planningService = planningService;
    }

    @PostMapping
    public ResponseEntity<PlanningService.SnapshotDetail> run(@RequestBody HouseholdInput input, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planningService.run(input, auth.getName()));
    }

    @GetMapping
    public List<PlanningService.SnapshotSummary> list(Authentication auth) {
        return planningService.list(auth.getName());
    }

    @GetMapping("/{id}")
    public PlanningService.SnapshotDetail get(@PathVariable UUID id, Authentication auth) {
        return planningService.get(id, auth.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, Authentication auth) {
        planningService.delete(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(PlanningService.InvalidHousehold.class)
    public ResponseEntity<Map<String, Object>> invalid(PlanningService.InvalidHousehold e) {
        return ResponseEntity.badRequest().body(Map.of("message", "Please check the form.", "errors", e.errors()));
    }
}

package com.hrragassistant.backend.planning;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrragassistant.backend.planning.analysis.HouseholdHealthCheck;
import com.hrragassistant.backend.planning.analysis.HouseholdInput;
import com.hrragassistant.backend.planning.analysis.HouseholdInputValidator;

/**
 * Runs the family financial health check and keeps each run as a snapshot
 * that only its owner can see. Everything is computed in Java; no AI call.
 */
@Service
public class PlanningService {

    static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final HouseholdSnapshotRepository repository;

    public PlanningService(HouseholdSnapshotRepository repository) {
        this.repository = repository;
    }

    public record SnapshotSummary(UUID id, Instant createdAt, String country, Integer score, String rating) {
    }

    public record SnapshotDetail(UUID id, Instant createdAt, HouseholdInput input, HouseholdHealthCheck result) {
    }

    /** Thrown with every validation message so the form can show them together. */
    public static class InvalidHousehold extends RuntimeException {
        private final List<String> errors;

        InvalidHousehold(List<String> errors) {
            super(String.join(" ", errors));
            this.errors = errors;
        }

        public List<String> errors() {
            return errors;
        }
    }

    public SnapshotDetail run(HouseholdInput input, String username) {
        int year = LocalDate.now().getYear();
        List<String> errors = HouseholdInputValidator.validate(input, year);
        if (!errors.isEmpty()) {
            throw new InvalidHousehold(errors);
        }
        HouseholdHealthCheck result = HouseholdHealthCheck.run(input, year);
        try {
            HouseholdSnapshot s = repository.save(new HouseholdSnapshot(username, input.country().name(),
                    result.health().score(), result.health().rating(), JSON.writeValueAsString(input), JSON.writeValueAsString(result)));
            return new SnapshotDetail(s.getId(), s.getCreatedAt(), input, result);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Could not store the health check", e);
        }
    }

    public List<SnapshotSummary> list(String username) {
        return repository.findByUsernameOrderByCreatedAtDesc(username).stream()
                .map(s -> new SnapshotSummary(s.getId(), s.getCreatedAt(), s.getCountry(), s.getScore(), s.getRating()))
                .toList();
    }

    public SnapshotDetail get(UUID id, String username) {
        HouseholdSnapshot s = own(id, username);
        try {
            return new SnapshotDetail(s.getId(), s.getCreatedAt(),
                    JSON.readValue(s.getInputJson(), HouseholdInput.class),
                    JSON.readValue(s.getResultJson(), HouseholdHealthCheck.class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Stored health check could not be read", e);
        }
    }

    public void delete(UUID id, String username) {
        repository.delete(own(id, username));
    }

    private HouseholdSnapshot own(UUID id, String username) {
        // Someone else's snapshot looks exactly like a missing one.
        return repository.findByIdAndUsername(id, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Health check not found"));
    }
}

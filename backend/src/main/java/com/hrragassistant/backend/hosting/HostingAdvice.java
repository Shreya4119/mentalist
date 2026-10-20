package com.hrragassistant.backend.hosting;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns daily limits and rate limits into 429 {message} bodies the frontend shows as-is. */
@RestControllerAdvice
public class HostingAdvice {

    @ExceptionHandler(LimitReached.class)
    public ResponseEntity<Map<String, Object>> limitReached(LimitReached e) {
        var response = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        if (e.retryAfterSeconds() > 0) {
            response.header(HttpHeaders.RETRY_AFTER, Long.toString(e.retryAfterSeconds()));
        }
        return response.body(Map.of("message", e.getMessage()));
    }
}

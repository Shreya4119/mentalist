package com.hrragassistant.backend.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * An upload over the servlet's multipart limit is rejected before any
 * controller runs; this turns it into a clear 413 message instead of a
 * generic error.
 */
@RestControllerAdvice
public class UploadLimitAdvice {

    private final long maxUploadMb;

    public UploadLimitAdvice(@Value("${app.reports.max-upload-mb:250}") long maxUploadMb) {
        this.maxUploadMb = maxUploadMb;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("message",
                "That upload is larger than the " + maxUploadMb + " MB limit. Upload just the financial statements pages, or compress the PDF."));
    }
}

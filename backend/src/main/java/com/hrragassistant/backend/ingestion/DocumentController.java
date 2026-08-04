package com.hrragassistant.backend.ingestion;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class DocumentController {

    private final DocumentIngestionService ingestionService;
    private final HrDocumentRepository documentRepository;

    public DocumentController(DocumentIngestionService ingestionService, HrDocumentRepository documentRepository) {
        this.ingestionService = ingestionService;
        this.documentRepository = documentRepository;
    }

    @PostMapping("/api/documents")
    public ResponseEntity<DocumentResponse> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uploaded file is empty");
        }

        HrDocument hrDocument = ingestionService.ingest(file);

        HttpStatus status = hrDocument.getStatus() == DocumentStatus.FAILED
                ? HttpStatus.UNPROCESSABLE_ENTITY
                : HttpStatus.CREATED;

        return ResponseEntity.status(status).body(DocumentResponse.from(hrDocument));
    }

    @GetMapping("/api/documents/{id}")
    public DocumentResponse status(@PathVariable UUID id) {
        HrDocument hrDocument = documentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + id));

        return DocumentResponse.from(hrDocument);
    }

    public record DocumentResponse(
            UUID id,
            String filename,
            DocumentStatus status,
            Integer chunkCount,
            String errorMessage,
            Instant uploadedAt,
            Instant indexedAt) {

        static DocumentResponse from(HrDocument doc) {
            return new DocumentResponse(
                    doc.getId(),
                    doc.getFilename(),
                    doc.getStatus(),
                    doc.getChunkCount(),
                    doc.getErrorMessage(),
                    doc.getUploadedAt(),
                    doc.getIndexedAt());
        }
    }
}

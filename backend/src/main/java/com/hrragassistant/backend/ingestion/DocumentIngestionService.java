package com.hrragassistant.backend.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import com.hrragassistant.backend.ingestion.storage.ReportFileStore;

/**
 * Phase 3: ingestion is async. enqueue() persists the upload and hands off
 * to SQS (LocalStack); processQueuedDocument() -- invoked by
 * SqsIngestionPoller -- does the actual parse -> chunk -> embed -> store
 * work in the background.
 */
@Service
public class DocumentIngestionService {

    private static final int CHUNK_SIZE_TOKENS = 500;
    private static final int CHUNK_OVERLAP_TOKENS = 50;

    private final StoredDocumentRepository documentRepository;
    private final VectorStore vectorStore;
    private final SqsClient sqsClient;
    private final SqsQueueInitializer sqsQueueInitializer;
    private final ReportFileStore fileStore;

    public DocumentIngestionService(
            StoredDocumentRepository documentRepository,
            VectorStore vectorStore,
            SqsClient sqsClient,
            SqsQueueInitializer sqsQueueInitializer,
            ReportFileStore fileStore) {
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
        this.sqsClient = sqsClient;
        this.sqsQueueInitializer = sqsQueueInitializer;
        this.fileStore = fileStore;
    }

    /** Streams an upload straight to the file store (never fully into memory) and queues it for indexing. */
    public StoredDocument enqueue(MultipartFile file, DocumentKind kind) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed-document";
        String ext = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT) : "";
        ReportFileStore.StoredFile stored;
        try (var in = file.getInputStream()) {
            stored = fileStore.save(in, ext.matches("[a-z0-9]{1,5}") ? ext : "");
        }
        return enqueueStored(filename, stored, null, kind);
    }

    /** Registers a file that is already in the file store and queues it for indexing. */
    public StoredDocument enqueueStored(String filename, ReportFileStore.StoredFile stored, String mediaType, DocumentKind kind) {
        StoredDocument storedDoc = new StoredDocument(filename, stored.relativePath(), stored.sizeBytes(), stored.sha256(), mediaType, kind);
        documentRepository.save(storedDoc);
        sendToQueue(storedDoc.getId());
        return storedDoc;
    }

    /**
     * A readable path to the document's file. Old rows keep their bytes in
     * Postgres; those are written to a temporary file that is deleted on close.
     */
    public FileHandle open(StoredDocument doc) throws IOException {
        if (doc.getStoragePath() != null) {
            return new FileHandle(fileStore.resolve(doc.getStoragePath()), false);
        }
        if (doc.getContent() == null) {
            throw new IOException("The report file is missing");
        }
        Path tmp = Files.createTempFile("mentalist-", "-" + doc.getId());
        Files.write(tmp, doc.getContent());
        return new FileHandle(tmp, true);
    }

    public record FileHandle(Path path, boolean temporary) implements AutoCloseable {
        @Override
        public void close() {
            if (temporary) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // temp files are cleaned up by the OS eventually
                }
            }
        }
    }

    /**
     * Re-queues documents a previous run left QUEUED or PROCESSING. Their SQS
     * message may already be consumed (processing died with the old process),
     * so without this they would sit at PROCESSING forever. A document that
     * is picked up twice is harmless: processing clears its earlier chunks first.
     */
    public int requeueUnfinished() {
        List<StoredDocument> unfinished = documentRepository.findAllByStatusIn(List.of(DocumentStatus.QUEUED, DocumentStatus.PROCESSING));
        for (StoredDocument doc : unfinished) {
            doc.markQueued();
            documentRepository.save(doc);
            sendToQueue(doc.getId());
        }
        return unfinished.size();
    }

    private void sendToQueue(UUID documentId) {
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(sqsQueueInitializer.getQueueUrl())
                .messageBody(documentId.toString())
                .build());
    }

    public void processQueuedDocument(UUID documentId) {
        StoredDocument storedDoc = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("Queued document not found: " + documentId));

        storedDoc.markProcessing();
        documentRepository.save(storedDoc);

        try {
            List<Document> chunks;
            try (FileHandle file = open(storedDoc)) {
                chunks = parseAndSplit(file.path(), storedDoc.getFilename(), documentId.toString());
            }
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("No extractable text found in the uploaded file");
            }
            // A re-run (after an interrupted one) must not leave duplicate chunks behind.
            // Supplemental text (chunk_index -1, e.g. a report's AI analysis) is kept.
            vectorStore.delete("document_id == '" + documentId + "' && chunk_index >= 0");

            vectorStore.add(chunks);
            storedDoc.markIndexed(chunks.size());
        } catch (Exception e) {
            storedDoc.markFailed(e.getMessage());
        }

        documentRepository.save(storedDoc);
    }

    /**
     * Removes a document and, if it was indexed, its chunks from the vector
     * store (matched on the document_id metadata every chunk carries).
     * Past chat citations keep their copied chunk text, so old conversations
     * still render after the source document is gone.
     */
    public void delete(UUID documentId) {
        StoredDocument storedDoc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + documentId));

        // Delete by metadata whatever the status: supplemental text (chunk_index -1)
        // can exist even when the file's own text extraction failed.
        vectorStore.delete("document_id == '" + documentId + "'");
        documentRepository.delete(storedDoc);
        fileStore.delete(storedDoc.getStoragePath());
    }

    /**
     * Adds extra searchable text for a document (e.g. the AI analysis of a
     * financial report, so chat can cite its computed ratios). If the file
     * itself had no extractable text -- a scanned, image-only report -- this
     * text makes the document chat-ready anyway.
     */
    public void addSupplementalText(UUID documentId, String label, String text) {
        StoredDocument storedDoc = documentRepository.findById(documentId).orElse(null);
        if (storedDoc == null || text == null || text.isBlank()) {
            return;
        }
        vectorStore.delete("document_id == '" + documentId + "' && chunk_index == -1");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("document_id", documentId.toString());
        metadata.put("filename", label);
        metadata.put("chunk_index", -1);
        vectorStore.add(List.of(new Document(text, metadata)));

        if (storedDoc.getStatus() == DocumentStatus.FAILED) {
            storedDoc.markIndexed(1);
            documentRepository.save(storedDoc);
        }
    }

    private List<Document> parseAndSplit(Path file, String filename, String documentId) {
        // Read from disk so large reports are streamed by Tika rather than held in memory twice.
        Resource resource = new FileSystemResource(file) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        List<Document> parsed = new TikaDocumentReader(resource).read();

        OverlappingTokenTextSplitter splitter = new OverlappingTokenTextSplitter(CHUNK_SIZE_TOKENS, CHUNK_OVERLAP_TOKENS);
        List<Document> rawChunks = splitter.apply(parsed);

        List<Document> chunks = new ArrayList<>(rawChunks.size());
        for (int i = 0; i < rawChunks.size(); i++) {
            Document rawChunk = rawChunks.get(i);

            Map<String, Object> metadata = new HashMap<>(rawChunk.getMetadata());
            metadata.put("document_id", documentId);
            metadata.put("filename", filename);
            metadata.put("chunk_index", i);

            chunks.add(new Document(rawChunk.getText(), metadata));
        }

        return chunks;
    }
}

package com.hrragassistant.backend.ingestion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

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

    private final HrDocumentRepository documentRepository;
    private final VectorStore vectorStore;
    private final SqsClient sqsClient;
    private final SqsQueueInitializer sqsQueueInitializer;

    public DocumentIngestionService(
            HrDocumentRepository documentRepository,
            VectorStore vectorStore,
            SqsClient sqsClient,
            SqsQueueInitializer sqsQueueInitializer) {
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
        this.sqsClient = sqsClient;
        this.sqsQueueInitializer = sqsQueueInitializer;
    }

    public HrDocument enqueue(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed-document";

        HrDocument hrDocument = new HrDocument(filename, file.getBytes());
        documentRepository.save(hrDocument);

        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(sqsQueueInitializer.getQueueUrl())
                .messageBody(hrDocument.getId().toString())
                .build());

        return hrDocument;
    }

    public void processQueuedDocument(UUID documentId) {
        HrDocument hrDocument = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("Queued document not found: " + documentId));

        hrDocument.markProcessing();
        documentRepository.save(hrDocument);

        try {
            List<Document> chunks = parseAndSplit(hrDocument.getContent(), hrDocument.getFilename(), documentId.toString());
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("No extractable text found in the uploaded file");
            }

            vectorStore.add(chunks);
            hrDocument.markIndexed(chunks.size());
        } catch (Exception e) {
            hrDocument.markFailed(e.getMessage());
        }

        documentRepository.save(hrDocument);
    }

    private List<Document> parseAndSplit(byte[] content, String filename, String documentId) {
        Resource resource = new ByteArrayResource(content) {
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

package com.hrragassistant.backend.ingestion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Phase 1: synchronous ingestion (parse -> chunk -> embed -> store) within
 * the request. Async processing via SQS is a Phase 3 concern; the HrDocument
 * status field already models that transition so the API shape won't need
 * to change when it lands.
 */
@Service
public class DocumentIngestionService {

    private static final int CHUNK_SIZE_TOKENS = 500;
    private static final int CHUNK_OVERLAP_TOKENS = 50;

    private final HrDocumentRepository documentRepository;
    private final VectorStore vectorStore;

    public DocumentIngestionService(HrDocumentRepository documentRepository, VectorStore vectorStore) {
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
    }

    public HrDocument ingest(MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed-document";

        HrDocument hrDocument = new HrDocument(filename);
        documentRepository.save(hrDocument);

        try {
            List<Document> chunks = parseAndSplit(file, filename, hrDocument.getId().toString());
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("No extractable text found in the uploaded file");
            }

            vectorStore.add(chunks);
            hrDocument.markIndexed(chunks.size());
        } catch (Exception e) {
            hrDocument.markFailed(e.getMessage());
        }

        documentRepository.save(hrDocument);
        return hrDocument;
    }

    private List<Document> parseAndSplit(MultipartFile file, String filename, String documentId) throws IOException {
        Resource resource = new ByteArrayResource(file.getBytes()) {
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

package com.hrragassistant.backend.ingestion;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Phase 0: seeds a single hardcoded HR policy snippet so the RAG pipeline
 * (embed -> store -> retrieve -> generate) can be proven end-to-end before
 * a real document-ingestion API exists.
 */
@Component
public class DocumentSeeder implements ApplicationRunner {

    private static final String SEED_DOC_ID = "leave-policy-carryover";

    private static final String LEAVE_POLICY_TEXT = """
            Leave and Vacation Policy - Carryover Rules

            Full-time employees accrue 20 vacation days per calendar year. At the end of the
            calendar year, employees may carry over a maximum of 5 unused vacation days into
            the following year. Carried-over days must be used by March 31st of the new year,
            after which any remaining carried-over balance is forfeited. Vacation days beyond
            the 5-day carryover cap are paid out at the employee's base daily rate in the
            December payroll run. Part-time employees accrue vacation on a pro-rated basis and
            are subject to the same 5-day carryover cap, pro-rated by their contracted hours.
            """;

    private final VectorStore vectorStore;

    public DocumentSeeder(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean alreadySeeded = !vectorStore
                .similaritySearch(SearchRequest.builder()
                        .query("vacation day carryover policy")
                        .topK(1)
                        .filterExpression("doc_id == '%s'".formatted(SEED_DOC_ID))
                        .build())
                .isEmpty();

        if (alreadySeeded) {
            return;
        }

        Document seedDocument = new Document(
                LEAVE_POLICY_TEXT,
                Map.of("doc_id", SEED_DOC_ID, "source", "hardcoded-seed"));

        vectorStore.add(List.of(seedDocument));
    }
}

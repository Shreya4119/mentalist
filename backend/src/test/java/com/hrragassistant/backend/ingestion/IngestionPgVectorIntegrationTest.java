package com.hrragassistant.backend.ingestion;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sqs.SqsClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spins up a real Postgres+pgvector (per spec: "integration tests with
 * Testcontainers -- spin up a real Postgres+pgvector in tests") plus a
 * throwaway Redis so the full app context boots cleanly, independent of the
 * dev docker-compose stack. SQS is mocked -- this test exercises
 * processQueuedDocument() directly rather than the queue/poller, so nothing
 * needs to actually reach LocalStack. Embeddings use the host's
 * already-required Ollama daemon rather than testcontainers-ollama, to
 * avoid pulling model weights on every test run.
 */
@SpringBootTest
@Testcontainers
class IngestionPgVectorIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.docker.compose.enabled", () -> "false");
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @MockitoBean
    SqsClient sqsClient;

    @MockitoBean
    SqsQueueInitializer sqsQueueInitializer;

    @Autowired
    DocumentIngestionService ingestionService;

    @Autowired
    StoredDocumentRepository documentRepository;

    @Autowired
    VectorStore vectorStore;

    @Test
    void ingestsDocumentAndMakesItRetrievableViaSimilaritySearch() throws Exception {
        String reportText = "Northwind Traders - Annual Report FY2025-26, Liquidity\n\n"
                + "Current assets were EUR 480 million against current liabilities of EUR 300 million, "
                + "giving a current ratio of 1.6 at the balance-sheet date.";

        MockMultipartFile file = new MockMultipartFile(
                "file", "northwind-fy26.txt", "text/plain", reportText.getBytes());

        StoredDocument queued = ingestionService.enqueue(file, DocumentKind.FINANCIAL_REPORT);
        assertThat(queued.getStatus()).isEqualTo(DocumentStatus.QUEUED);

        ingestionService.processQueuedDocument(queued.getId());

        StoredDocument indexed = documentRepository.findById(queued.getId()).orElseThrow();
        assertThat(indexed.getStatus()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(indexed.getChunkCount()).isEqualTo(1);

        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query("What is the current ratio?").topK(1).build());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMetadata().get("document_id")).isEqualTo(queued.getId().toString());
        assertThat(results.get(0).getText()).contains("current ratio of 1.6");
    }
}

# HR/Payroll Knowledge Assistant (RAG)

An internal assistant that answers natural-language HR and payroll questions —
*"How many vacation days carry over into next year?"* — grounded in a
company's own policy documents, with answers traceable back to source text.

**Status: Phase 0 — Skeleton.** The pipeline (embed → store → retrieve →
generate) works end-to-end against one hardcoded document and one REST
endpoint. Real document ingestion, auth, caching, and the Angular UI come in
later phases.

## Architecture

```
Angular chat UI (later)  --->  Spring Boot API (Spring AI ChatClient)
                                        |
                                        |-- QuestionAnswerAdvisor
                                        |     |
                                        |     '-- similarity search --> PostgreSQL + pgvector
                                        |
                                        '-- generation --> Amazon Bedrock (Claude, via Converse API)

Embeddings: Amazon Bedrock Titan (amazon.titan-embed-text-v2:0, 1024 dims)
```

- **Backend**: Java 21, Spring Boot 4, Spring AI 2 (`backend/`)
- **Vector store**: PostgreSQL with the `pgvector` extension
- **LLM + embeddings**: Amazon Bedrock (Claude for generation, Titan for embeddings)
- **Frontend**: Angular — not built yet (Phase 4)

## Design decisions (Phase 0)

- **Retrieval**: Spring AI's built-in `QuestionAnswerAdvisor` handles
  similarity search + prompt augmentation — no hand-rolled retrieval code.
- **Grounding**: the seeded document is deliberately narrow (one leave policy
  paragraph) so it's obvious whether an answer is actually grounded in it.
  Anti-hallucination prompting and explicit citations are a Phase 1 concern.
- **Credentials**: AWS credentials are never read from application config —
  Spring AI's Bedrock client uses the standard AWS SDK credential chain
  (environment variables, `~/.aws/credentials`, or an instance profile).
- **Local Postgres lifecycle**: `spring-boot-docker-compose` auto-starts/stops
  `docker-compose.yml` when the app runs locally, so there's no separate
  "remember to start the DB" step during dev.

## Prerequisites

- Java 21
- Docker Desktop (for Postgres + pgvector)
- AWS credentials with Bedrock access, configured via `aws configure` or
  `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` / `AWS_REGION` env vars
- The chat model in `backend/src/main/resources/application.yml`
  (`spring.ai.bedrock.converse.chat.model`, currently
  `us.anthropic.claude-haiku-4-5-20251001-v1:0`) must be enabled for your AWS
  account/region in the Bedrock console — model access is opt-in per model.

## Running it

```bash
cd backend
./mvnw spring-boot:run
```

This starts Postgres via Docker Compose automatically, runs Flyway-free
schema init for the pgvector table, seeds one hardcoded leave-policy
paragraph on first boot, and starts the API on port 8080.

Ask it a question:

```bash
curl -X POST localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question":"How many vacation days carry over into next year?"}'
```

Expected: an answer grounded in the seeded policy text (5-day cap, use by
March 31st).

## Roadmap

- **Phase 1** — real document ingestion API (upload → chunk → embed → store),
  citations in chat responses, conversation history.
- **Phase 2** — JWT/RBAC security, Redis caching, multi-turn chat memory.
- **Phase 3** — async ingestion via SQS, cost/latency observability,
  Testcontainers integration tests.
- **Phase 4** — Angular chat UI, Docker, CI/CD, AWS deployment.

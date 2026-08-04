# HR/Payroll Knowledge Assistant (RAG)

An internal assistant that answers natural-language HR and payroll questions —
*"How many vacation days carry over into next year?"* — grounded in a
company's own policy documents, with answers traceable back to source text.

**Status: Phase 2 — Backend rigor.** Real document ingestion, retrieval with
citations, conversation history/feedback, JWT/RBAC auth, Redis-backed
caching, and multi-turn conversation memory are all working end-to-end.
Async ingestion and the Angular UI come in later phases.

## Architecture

```
Angular chat UI (later)  --->  Spring Boot API (Spring AI ChatClient)
                                        |
                          +-------------+-------------+
                          |                           |
                 POST /api/documents           POST /api/chat
                          |                           |
                 Tika parse -> chunk         QuestionAnswerAdvisor
                 (overlapping token          similarity search --> PostgreSQL + pgvector
                  splitter) -> embed                  |
                          |                    generation --> Ollama (llama3.2)
                          v                           |
                 PostgreSQL + pgvector        citations + conversation/message
                 (chunks + embeddings)        history persisted (JPA/Postgres)

Embeddings: Ollama nomic-embed-text (768 dims), served by a local Ollama daemon
```

- **Backend**: Java 21, Spring Boot 4, Spring AI 2 (`backend/`)
- **Vector store**: PostgreSQL with the `pgvector` extension
- **Relational data**: documents, conversations, messages, citations, and
  feedback are plain JPA entities in the same Postgres instance
- **LLM + embeddings**: local Ollama (`llama3.2` for chat, `nomic-embed-text`
  for embeddings) — free, no cloud account. Amazon Bedrock (Claude + Titan)
  remains a documented, config-only swap (see "Switching back to Bedrock")
- **Frontend**: Angular — not built yet (Phase 4)

## Design decisions

- **Chunking**: a custom `OverlappingTokenTextSplitter` slides a 500-token
  window with 50-token overlap across each document, using the same
  tokenizer (`cl100k_base` via jtokkit) Spring AI's own splitter uses.
  Spring AI's built-in `TokenTextSplitter` doesn't support overlap natively
  (see [spring-projects/spring-ai#2123](https://github.com/spring-projects/spring-ai/issues/2123)),
  so this fills that gap directly rather than dropping the requirement.
- **Parsing**: `TikaDocumentReader` handles PDF/DOCX/TXT (and most other
  common formats) via content-type sniffing — no manual format branching.
- **Retrieval**: Spring AI's `QuestionAnswerAdvisor` does similarity search +
  prompt augmentation, configured for `topK(5)` per the spec's 4–6
  recommendation.
- **Grounding / anti-hallucination**: the shared `ChatClient` carries a
  default system prompt instructing the model to answer only from the
  provided context and say *"I don't know based on the available documents"*
  otherwise.
- **Citations**: each assistant answer persists the exact retrieved chunks
  (source document, filename, chunk index, chunk text) used to generate
  it — pulled straight off `QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS` in the
  `ChatResponse` metadata, so citations are what was *actually* used, not a
  re-derived guess.
- **Ingestion is synchronous for now**: `POST /api/documents` parses, chunks,
  embeds, and stores within the request. The `status` field
  (`PROCESSING`/`INDEXED`/`FAILED`) already models the async case so Phase
  3's SQS-based pipeline can slot in later without changing the API shape.
- **Local-first model provider**: Ollama needs no account, no card, and no
  internet after the models are pulled — see "Switching back to Bedrock" for
  why this doesn't cost the project its cloud-AI story.
- **Schema management**: `spring.jpa.hibernate.ddl-auto=update` auto-creates
  the relational tables for now. That's a dev-only convenience — a real
  migration tool (Flyway/Liquibase) is the honest choice once this needs to
  run anywhere besides a laptop, and is a natural Phase 3 addition alongside
  the other "production patterns."
- **Auth**: hand-rolled JWT (via `jjwt`), not
  `spring-security-oauth2-resource-server` — that starter targets validating
  tokens from an *external* identity provider, and this app is both issuer
  and validator, so a plain `JwtService` + a request filter is the
  right-sized tool. Two seeded demo accounts (`hr_admin` / `employee`, see
  below) stand in for a registration flow, which is out of scope — the point
  of this phase is proving RBAC actually gates `/api/documents/**` to
  `HR_ADMIN`, not building account management.
- **Caching vs. conversation memory**: the spec asks for caching "answers for
  repeated questions," but once memory (this phase) feeds prior turns into
  the prompt, an identical question can legitimately get a different answer
  depending on conversation context. Resolution: full answers are cached in
  Redis only for **stateless, first-turn questions** (no `conversationId` on
  the request) — the common "many employees ask the same FAQ" case — and the
  cache is always bypassed once a conversation has follow-up turns.
  Embeddings, by contrast, are cached unconditionally: they're a pure
  function of the input text, so there's no such conflict. The embedding
  cache wraps the `EmbeddingModel` bean itself (`CachingEmbeddingModel`,
  `@Primary`), so ingestion and retrieval both benefit with no code changes
  of their own.
- **Conversation memory**: continuing a conversation loads its last 10
  persisted messages (5 exchanges) and passes them to the `ChatClient` as
  prior turns, so follow-ups actually have context. New conversations skip
  this entirely (nothing to load) and are the only ones eligible for answer
  caching.

## Prerequisites

- Java 21
- Docker Desktop (for Postgres + pgvector, and now Redis)
- [Ollama](https://ollama.com) running locally, with the two models pulled:
  ```bash
  ollama pull llama3.2
  ollama pull nomic-embed-text
  ```

## Running it

```bash
cd backend
./mvnw spring-boot:run
```

This starts Postgres and Redis via Docker Compose automatically, and
initializes the pgvector schema (768 dimensions, matching
`nomic-embed-text`) plus the JPA tables for documents, conversations,
messages, citations, feedback, and users. Two demo accounts are seeded on
first boot (dev-only, not for anything beyond a laptop):

| Username | Password | Role |
|---|---|---|
| `hr_admin` | `hr-admin-pass` | `HR_ADMIN` |
| `employee` | `employee-pass` | `EMPLOYEE` |

### 0. Log in

```bash
curl -X POST localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"hr_admin","password":"hr-admin-pass"}'
```
```json
{"token": "eyJ...", "role": "HR_ADMIN"}
```

Every other endpoint below needs `-H "Authorization: Bearer <token>"`.
`/api/documents/**` requires an `HR_ADMIN` token; `/api/chat/**` accepts
either role.

### 1. Upload a document

A sample policy document is included at `sample-data/leave-policy.txt`:

```bash
curl -F "file=@sample-data/leave-policy.txt" \
  -H "Authorization: Bearer <hr_admin token>" \
  localhost:8080/api/documents
```
```json
{"id":"...", "filename":"leave-policy.txt", "status":"INDEXED", "chunkCount":1, ...}
```

### 2. Check its status

```bash
curl -H "Authorization: Bearer <token>" localhost:8080/api/documents/{id}
```

### 3. Ask a question

```bash
curl -X POST localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"question":"How many vacation days carry over into next year?"}'
```
```json
{
  "conversationId": "...",
  "messageId": "...",
  "answer": "...",
  "citations": [{"documentId": "...", "filename": "leave-policy.txt", "chunkIndex": 0, "chunkText": "..."}]
}
```

Asking the exact same question again (still with no `conversationId`) comes
back from the Redis answer cache. Pass the returned `conversationId` on a
follow-up call to continue the same conversation with memory — and note
that path always calls the model fresh, never the cache.

### 4. Read the conversation back

```bash
curl -H "Authorization: Bearer <token>" localhost:8080/api/chat/{conversationId}
```

### 5. Leave feedback on an answer

```bash
curl -X POST localhost:8080/api/chat/{messageId}/feedback \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"rating":"UP"}'
```

## Switching back to Bedrock

Phase 0 originally targeted Amazon Bedrock (Claude for generation, Titan for
embeddings) per the spec, and the project intentionally kept that swap cheap:
replace the `spring-ai-starter-model-ollama` dependency with
`spring-ai-starter-model-bedrock-converse` + `spring-ai-starter-model-bedrock`,
point `spring.ai.bedrock.*` at your AWS region/model, and update
`spring.ai.vectorstore.pgvector.dimensions` to match Titan's embedding size
(1024) instead of `nomic-embed-text`'s 768. No RAG/ingestion/chat code
changes needed — the `ChatModel`/`EmbeddingModel`/`VectorStore` abstractions
are provider-agnostic.

## Roadmap

- **Phase 3** — async ingestion via SQS, cost/latency observability,
  Testcontainers integration tests.
- **Phase 4** — Angular chat UI, Docker, CI/CD, AWS deployment.

# HR/Payroll Knowledge Assistant (RAG)

An internal assistant that answers natural-language HR and payroll questions —
*"How many vacation days carry over into next year?"* — grounded in a
company's own policy documents, with answers traceable back to source text.

**Status: complete (all planned phases).** The full backend (ingestion,
retrieval with citations, JWT/RBAC, Redis caching, conversation memory,
async SQS ingestion, observability, Testcontainers) plus a working Angular
frontend all run end-to-end in the browser, both Docker images build and
run as a complete containerized stack, a GitHub Actions CI/CD pipeline is
written (unverified until pushed — see "CI/CD"), and the target AWS
deployment is fully expressed as validated Terraform without ever spending
a dollar on it (see `deploy/DEPLOYMENT.md`).

## Architecture

```
Angular SPA (frontend/, ng serve --> localhost:4200, proxies /api to :8080)
                                        |
                                        v
                                Spring Boot API (Spring AI ChatClient)
                                        |
                          +-------------+-------------+
                          |                           |
                 POST /api/documents           POST /api/chat
                 (202, status QUEUED)                  |
                          |                   QuestionAnswerAdvisor
                          v                   similarity search --> PostgreSQL + pgvector
                 SQS (LocalStack)                      |
                          |                    generation --> Ollama (llama3.2)
                          v                             |
              SqsIngestionPoller              citations + conversation/message
              (background, long-polling)      history persisted (JPA/Postgres)
                          |
                 Tika parse -> chunk
                 (overlapping token
                  splitter) -> embed
                          |
                          v
                 PostgreSQL + pgvector
                 (chunks + embeddings)

Embeddings: Ollama nomic-embed-text (768 dims), served by a local Ollama daemon
Metrics: Micrometer -> Actuator (/actuator/metrics, /actuator/prometheus)
```

- **Backend**: Java 21, Spring Boot 4, Spring AI 2 (`backend/`)
- **Vector store**: PostgreSQL with the `pgvector` extension
- **Relational data**: documents, conversations, messages, citations,
  feedback, and users are plain JPA entities in the same Postgres instance
- **Queue**: SQS via LocalStack (free, no AWS account) — plain AWS SDK v2
  `SqsClient`, not Spring Cloud AWS (see "Design decisions")
- **LLM + embeddings**: local Ollama (`llama3.2` for chat, `nomic-embed-text`
  for embeddings) — free, no cloud account. Amazon Bedrock (Claude + Titan)
  remains a documented, config-only swap (see "Switching back to Bedrock")
- **Frontend**: Angular 21, standalone components + signals, no NgModules
  (`frontend/`) — login, chat with citations/feedback, HR-admin document
  upload with status polling

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
- **Async ingestion (SQS via LocalStack)**: `POST /api/documents` now
  persists the upload (status `QUEUED`) and returns `202 Accepted`
  immediately; a background poller (`SqsIngestionPoller`, long-polling
  `receiveMessage`) does the actual parse/chunk/embed/store work. Uses the
  plain AWS SDK v2 `SqsClient` directly, not the Spring Cloud AWS SQS
  starter (`@SqsListener`) — that starter's confirmed compatibility only
  goes up to Spring Boot 3.2.x, and this project is on the very recent
  Spring Boot 4.1.0. The SDK client itself has zero Spring Boot version
  coupling, so this sidesteps the risk entirely (the same reasoning as
  hand-rolling JWT over a heavier framework piece in Phase 2). No S3: raw
  upload bytes are stashed in a new `hr_document.content bytea` column so
  the background consumer has something to process after the HTTP request
  completes — S3 (via LocalStack too) would be the production-shaped
  choice, kept out to avoid a second AWS service in an already-large phase.
  No retry/DLQ either: a failed message is still deleted (status becomes
  `FAILED`, visible via `GET /api/documents/{id}`) rather than redelivered
  — a natural "what would you add next" answer, not a gap to hide.
- **Observability**: `ChatService.ask()` records a Micrometer `Timer`
  (`chat.request.latency`, tagged `cache=hit|miss`) and token/cost counters
  (`chat.tokens{type=prompt|completion}`, `chat.cost.usd`) via Actuator.
  Ollama itself is free, so the cost figure is illustrative — computed from
  a configurable `$/1k tokens` rate — the point is proving the
  instrumentation exists for when a paid provider (e.g. Bedrock) is swapped
  back in. Cache hits record zero token/cost metrics, honestly reflecting
  that no model call happened.
- **Testcontainers**: one integration test spins up a *real*
  Postgres+pgvector container (per the spec's explicit ask) and asserts
  ingestion produces the right chunk count and that a similarity search for
  the seeded question returns the correct chunk as the top hit — a
  deterministic, testable claim, unlike asserting on generated LLM prose. A
  throwaway Redis container is included too so the full app context boots
  cleanly and hermetically; SQS is mocked (the test calls
  `processQueuedDocument()` directly rather than going through the queue).
  Embeddings still use the host's already-required Ollama daemon rather
  than `testcontainers-ollama`, to avoid pulling model weights on every
  test run — a fully hermetic CI setup would add that module later.
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
- **Frontend/backend integration — dev-server proxy, not CORS**: the Angular
  dev server proxies `/api/**` to `localhost:8080` (`frontend/proxy.conf.json`),
  so browser requests are same-origin during `ng serve` and `SecurityConfig`
  needed zero changes for this phase. The app calls relative `/api/...`
  paths throughout, which also keeps it deployment-flexible — a real deploy
  would serve both from one origin or a reverse proxy rather than needing
  CORS at all.
- **Frontend auth**: the JWT lives in `localStorage` (survives reloads; the
  tradeoff is XSS exposure vs. an httpOnly cookie, which would need backend
  changes too — reasonable for a demo app, not silently accepted). A
  functional `authInterceptor` attaches the bearer token and treats any
  401/403 as "session invalid" (logout + redirect to `/login`); a
  functional `authGuard`/`hrAdminGuard` pair gates routes by
  authentication/role. There's no refresh-token flow — the JWT expires
  after 1 hour and the interceptor's 401/403 handling is what catches that,
  not a silent refresh.
- **No conversation-list UI**: one active conversation per browser session,
  matching the spec's literal "Angular chat UI" ask and the fact that the
  backend has no endpoint to enumerate a user's past conversations anyway.
- **Two docker-compose files, not one**: `docker-compose.yml` stays
  dev-infra-only (Postgres/Redis/LocalStack, auto-managed by Spring Boot's
  own docker-compose lifecycle during `mvn spring-boot:run`).
  `docker-compose.prod.yml` is a separate, complete containerized stack
  (backend + frontend + infra) — different purpose, different lifecycle,
  shouldn't be conflated. It uses an explicit Compose project name
  (confirmed necessary — without it, the two files' same-named services
  collided under the directory-derived default project name and `up`
  recreated the dev stack's containers in place instead of creating
  separate ones).
- **AWS deployment is docs/Terraform-only, never applied**: consistent
  with every other cost-avoidance decision in this project (Ollama instead
  of Bedrock, LocalStack instead of real SQS) — no AWS account was ever
  needed to build or run this. `deploy/terraform/` is validated with
  `terraform validate` against the real AWS provider schema (confirmed
  clean), not just written and hoped; `deploy/DEPLOYMENT.md` covers what it
  provisions and what's simplified (default VPC, no Multi-AZ, etc).
- **CI publishes to GHCR, not ECR**: pushing to ECR from CI would need AWS
  credentials as GitHub secrets, which doesn't fit "no AWS account." GHCR
  needs zero setup (the built-in `GITHUB_TOKEN`) and still proves the full
  build → test → publish pipeline. `.github/workflows/ci.yml` is
  unverified in the sense that it's never actually run — this repo has no
  GitHub remote — but is written to current, confirmed action versions.

## Prerequisites

- Java 21
- Docker Desktop (for Postgres + pgvector, Redis, and now LocalStack)
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

A sample policy document is included at `sample-data/leave-policy.txt`.
Ingestion is async now, so the response comes back immediately with status
`QUEUED`:

```bash
curl -F "file=@sample-data/leave-policy.txt" \
  -H "Authorization: Bearer <hr_admin token>" \
  localhost:8080/api/documents
```
```json
{"id":"...", "filename":"leave-policy.txt", "status":"QUEUED", "chunkCount":null, ...}
```
```
HTTP/1.1 202 Accepted
```

### 2. Poll its status

A background poller picks the message off the SQS (LocalStack) queue within
a second or two and processes it — `QUEUED` → `PROCESSING` → `INDEXED` (or
`FAILED`, with `errorMessage` set, if parsing failed):

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

## Running the frontend

With the backend already running (previous section) and Docker/Ollama up:

```bash
cd frontend
npm install
npm start
```

Opens at `http://localhost:4200`, proxying `/api/**` to the backend on
`:8080` (see `proxy.conf.json`). Log in with either demo account from the
table above — the "Documents" nav link only appears for `hr_admin`.

## Metrics

```bash
curl localhost:8080/actuator/metrics/chat.request.latency
curl localhost:8080/actuator/metrics/chat.tokens
curl localhost:8080/actuator/metrics/chat.cost.usd
curl localhost:8080/actuator/prometheus   # Prometheus-scrape-ready format
```

`/actuator/**` is left open (no auth) for local dev/demo convenience — a
real deployment would put it behind a separate management port or network
policy rather than leave it public.

## Running tests

```bash
cd backend
./mvnw test
```

`IngestionPgVectorIntegrationTest` spins up real Postgres+pgvector (and a
throwaway Redis) via Testcontainers, independent of the dev database, and
needs Docker running — nothing else from the dev stack needs to be up.

## Running it fully containerized

No local Java/Maven/Node needed — just Docker and a host-run Ollama
(consistent with every other phase, Ollama is never containerized):

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Brings up the entire stack (backend, frontend, Postgres+pgvector, Redis,
LocalStack) under its own Compose project name
(`hr-rag-assistant-prod`, kept separate from the dev stack on purpose — see
"Design decisions"). Give the backend ~15–20s to finish booting on first
run, then:

- Frontend: `http://localhost:8081`
- Backend directly: `http://localhost:8080`

Verified end-to-end: both images build clean, the full stack starts, login
and chat work through the containerized frontend's nginx → containerized
backend → containerized Postgres/Redis/LocalStack → host Ollama via
`host.docker.internal`.

`docker compose -f docker-compose.prod.yml down` tears it back down.

## CI/CD

`.github/workflows/ci.yml`: compiles and tests the backend (the
Testcontainers suite runs here too — GitHub-hosted runners have Docker
preinstalled, no extra setup), builds the frontend, and — only on push to
`main`, only if both pass — builds and publishes both Docker images to
GHCR. **Unverified**: this repo has no GitHub remote and there's no local
Actions runner available, so this workflow has never actually executed.
Written to each action's real current major version (checked against their
actual latest GitHub releases, not assumed) and well-established patterns,
but treat it as needing a first-real-push review, not a proven pipeline.

## AWS deployment

`deploy/DEPLOYMENT.md` documents the target architecture (ECS Fargate
backend behind an ALB, RDS PostgreSQL with pgvector, ElastiCache Redis,
real SQS, S3+CloudFront for the frontend, Secrets Manager) and
`deploy/terraform/` expresses it as Terraform — validated with
`terraform init && terraform validate` against the real AWS provider
schema (clean, no errors), never applied against a real account. Same
reasoning as the Ollama/LocalStack choices throughout this project: prove
it's deployable without spending anything to prove it.

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

## What a real production deployment would still need

Called out explicitly rather than left implicit — see `deploy/DEPLOYMENT.md`
for the full list: a dedicated VPC (this uses the account's default VPC),
Multi-AZ/autoscaling, a custom domain + ACM certificate, Flyway/Liquibase in
place of `ddl-auto=update`, a real user store in place of the two seeded
demo accounts, and rotating the checked-in dev-only JWT secret.

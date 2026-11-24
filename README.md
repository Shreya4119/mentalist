# Mentalist: understand company reports, learn to invest

Mentalist reads a company's annual report or balance sheet and explains its
financial health in plain language, for students, expats and working
professionals without a finance background. Upload a report and get a 0–100
health score, every ratio with its formula and meaning, charts, red flags,
and answers to your own questions, each traced back to the report. A
**Learn** section explains every term.

It is decision support and education, **not investment advice**; every
analysis says so.

> **History.** This project began as an HR/payroll knowledge assistant (RAG
> over policy documents). That feature has been removed; the Java package
> (`com.hrragassistant`), the database name (`hrrag`) and the `hr_document`
> table keep their original names so existing installs carry on unchanged.
> Rename them when Flyway migrations are introduced.

## Architecture

```
Angular 21 (Financial reports · Learn · report chat)
        │  /api/**  (JWT)
Spring Boot 4 / Java 21
  ├─ reports      upload, AI analysis (Gemini or Claude), ratios, health score, compare
  ├─ ingestion    SQS (LocalStack) → Tika → chunks → pgvector (Ollama nomic-embed-text)
  ├─ chat         questions about ONE report, answers cite the report and its analysis
  └─ auth         JWT, roles ADMIN / USER
        │
PostgreSQL + pgvector · Redis (answer + embedding cache) · LocalStack SQS
```

- **AI extracts, Java computes**: the model only reads figures from the
  report; every ratio and the health score are computed in tested Java code.
- **Chat is always scoped** to one report (`document_id` filter on retrieval).
- **Async everything**: ingestion runs via SQS, analysis via `@Async`; both
  recover after a restart.

## Prerequisites

- Java 21
- Docker Desktop (Postgres + pgvector, Redis, LocalStack)
- [Ollama](https://ollama.com) for embeddings (and local chat, if you don't
  use a cloud profile):
  ```bash
  ollama pull nomic-embed-text
  ollama pull llama3.2
  ```
- An AI key for report analysis in `backend/.env`: `GEMINI_API_KEY` and/or
  `ANTHROPIC_API_KEY` (see "Providers" below).

## Running it

```bash
# terminal 1 — backend (starts Postgres/Redis/LocalStack via Docker Compose)
cd backend
mvnw spring-boot:run "-Dspring-boot.run.profiles=gemini"     # Windows cmd/PowerShell
./mvnw spring-boot:run -Dspring-boot.run.profiles=gemini     # Git Bash / macOS / Linux

# terminal 2 — frontend
cd frontend
npm install
npm start          # http://localhost:4200, proxies /api/** to :8080
```

Demo accounts (seeded on first boot, dev-only):

| Username | Password | Role | Can |
|---|---|---|---|
| `admin` | `admin-pass` | `ADMIN` | manage every report |
| `demo` | `demo-pass` | `USER` | read every report, manage their own uploads |

**Upgrading from the HR version:** on the first start, `LegacyHrCleanup`
renames the old accounts (`hr_admin` → `admin`, `employee` → `demo`, with the
passwords above, keeping their reports), renames the roles, and deletes the
old HR policy documents and their indexed chunks. Browsers signed in to the
old version are asked to sign in again.

## Running tests

```bash
cd backend && mvnw test          # unit tests + Testcontainers pgvector test (needs Docker)
cd frontend && npm run e2e       # Playwright UI suite against a mocked API (no backend needed)
```

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

## Running it fully containerized

No local Java/Maven/Node needed — just Docker and a host-run Ollama
(consistent with every other phase, Ollama is never containerized):

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

Brings up the entire stack (backend, frontend, Postgres+pgvector, Redis,
LocalStack) under its own Compose project name
(`hr-rag-assistant-prod`, kept separate from the dev stack on purpose). Give the backend ~15–20s to finish booting on first
run, then:

- Frontend: `http://localhost:8081`
- Backend directly: `http://localhost:8080`

Verified end-to-end: both images build clean, the full stack starts, login
and chat work through the containerized frontend's nginx → containerized
backend → containerized Postgres/Redis/LocalStack → host Ollama via
`host.docker.internal`.

`docker compose -f docker-compose.prod.yml down` tears it back down.

## Running it as a public website

The same app runs on one small server for real users (Oracle Cloud Always
Free or a ~€6/month Hetzner server), with free HTTPS. On the public server:

- people **create an account** (username + password, no email) and can delete it, with all their data, at any time;
- **uploaded reports are private** to the person who uploaded them; reports the admin uploads appear for everyone as **Sample** reports;
- each account has a **daily AI allowance** (3 report analyses and 30 questions by default) and the whole site has a daily ceiling, so costs stay predictable; the admin is not limited;
- sign-in and sign-up are rate-limited, and the backend refuses to start with the development secrets from this repository.

```bash
./deploy/public/make-env.sh                                # once: domain, Gemini key, admin password
docker compose -f docker-compose.public.yml up -d --build  # Caddy (HTTPS) + app + Postgres + Redis + queue + Ollama
```

Step-by-step guide, costs and day-to-day commands: [deploy/PUBLIC_SERVER.md](deploy/PUBLIC_SERVER.md).
Locally none of this changes anything: no sign-up page, no limits.

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

## Financial reports: AI fundamental analysis

The **Financial reports** area (the home screen) reads company balance sheets and
annual reports — tables, charts, images and scanned pages included — and
produces a plain-language financial health check. It is decision support for
investors, **not investment advice**; every page says so.

### Workflow

1. **Upload** a report (PDF, PNG/JPG/WEBP/TIFF, XLSX/XLS/CSV, DOCX or TXT, up to 250 MB locally and 60 MB on the public server; several page photos at once become one report).
   Pick the AI provider if more than one is configured.
2. The report is stored as a `FINANCIAL_REPORT` document and two things run
   in the background:
   - **AI analysis** (`ReportAnalysisWorker`, `@Async`): the model reads the
     whole file natively and fills a fixed extraction schema.
   - **Indexing for chat** (the existing SQS → Tika → pgvector pipeline).
3. The **analysis page** shows: a 0–100 health score with six area scores,
   a plain-language summary, strengths and red flags with evidence, charts
   (performance and financial position, current vs prior period; assets vs
   funding), every ratio with its formula using the report's own figures,
   the source figures table with page references, what the report's charts
   show, management outlook, and how the figures were read.
4. **Ask questions about this report** opens the document-scoped chat with a
   financial-analyst prompt. The analysis itself is indexed too, so chat can
   cite the computed ratios.
5. **Compare** any two analysed reports (two years of one company, or two
   companies): scores side by side, what improved / got worse, ratio and
   figure tables, and warnings when currencies, units or industries differ.

### Design: the AI extracts, Java computes

The model is only asked to **read** 15 standard line items (current and
prior period, with page references) and write the narrative. All ratios and
the health score are computed in plain Java (`reports.analysis`):

- `RatioCalculator` — 16 ratios across profitability, liquidity, leverage,
  cash flow, efficiency and growth, with fixed formulas and published
  rule-of-thumb thresholds. Banks/insurers skip ratios that don't apply to
  them; negative equity, losses and zero interest are handled explicitly.
- `HealthScorer` — Healthy 100 / Caution 60 / Concern 20 per ratio, averaged
  per area, weighted (Profitability 25, Liquidity 20, Leverage 20, Cash flow
  20, Growth 10, Efficiency 5). Strong ≥ 75, Stable 55–74, Watch 35–54,
  Weak < 35; fewer than 4 ratios → "Insufficient data" instead of a score.
- `ReportComparator` — deterministic side-by-side comparison.

So numbers are reproducible and auditable, and a model arithmetic slip can't
change the verdict.

### Large files and other edge cases

Annual reports are often far bigger than what an AI model accepts in one go (Adyen's is 137 MB). Mentalist handles this before the model sees the file:

| Situation | What happens |
|---|---|
| PDF over the provider's size or page limit (Gemini ~48 MB / 500 pages, Claude ~22 MB / 100 pages) | Pages are scored and the financial statements, key figures, auditor's report, notes and outlook are kept (English, Dutch, German titles). The report page lists exactly which pages were analysed and why. |
| Still too big (image-heavy pages) | Key pages are rendered as compressed page images, then fewer pages, then text only; otherwise a clear message. |
| Scanned PDF or photos | Pages are spread across the report; chat says answers come from the analysis only. |
| Password-protected PDF | Clear message asking for an unlocked copy. Owner-only restrictions (no printing/copying) are removed automatically. |
| Damaged PDF | Sent as-is when within limits, else a clear message. |
| Large images | Scaled down to the provider's limit. TIFF/GIF/BMP are converted to PDF (multi-page TIFF supported). HEIC gets an "export as JPG" tip. |
| Renamed / fake files | Detected from the file's contents, not its name. |
| Same file uploaded twice | Detected by SHA-256; you are taken to the existing report. |
| Temporary AI errors (429, 5xx, overloaded, timeouts) | Retried 3 times with 15 s / 45 s back-off. |
| Few figures found | The report is flagged as possibly not meaningful. |

Uploads are streamed to disk (not held in memory or stored in Postgres). Settings:

| Env var | Default | Meaning |
|---|---|---|
| `REPORTS_MAX_UPLOAD_MB` | 250 | Max size of one upload |
| `REPORTS_MAX_REQUEST_MB` | 300 | Max request size (several photos) |
| `STORAGE_DIR` | `./data/uploads` (`/data/uploads` in Docker, on the `proddata_uploads` volume) | Where uploaded files are kept |
| `REPORTS_ANALYSIS_THREADS` | 2 | Analyses running at once |

The frontend's nginx allows 300 MB request bodies for `/api/`.

### Providers

| Provider | Setting | Default model | Native PDF limit | Notes |
|---|---|---|---|---|
| Google Gemini | `GEMINI_API_KEY` | `gemini-3.8-flash` | 50 MB / 1,000 pages (Files API above 15 MB) | Free tier may use content for training: public reports only |
| Anthropic Claude | `ANTHROPIC_API_KEY` | `claude-sonnet-5-5` | ~22 MB file (32 MB request) / 600 pages | Paid |

Put keys in `backend/.env`. Override models with `REPORTS_GEMINI_MODEL` /
`REPORTS_CLAUDE_MODEL`, the default with `REPORTS_DEFAULT_PROVIDER`. Files
over a provider's native limit, and spreadsheets/Word files, are analysed
from extracted text, and the analysis says so.

### API

| Method | Path | Who |
|---|---|---|
| GET | `/api/reports/providers` | any signed-in user |
| GET | `/api/reports` | any signed-in user |
| POST | `/api/reports` (multipart `file`, optional `provider`) → 202 | any signed-in user |
| GET | `/api/reports/{id}` | any signed-in user |
| POST | `/api/reports/{id}/analysis?provider=` → 202 | uploader or admin |
| DELETE | `/api/reports/{id}` | uploader or admin |
| GET | `/api/reports/compare?a=&b=` | any signed-in user |

Analyses interrupted by a restart are marked failed on startup, with a
"Re-analyze" button. Metric: `reports.analysis.latency` (tags provider,
outcome).

## Free cloud chat models (Gemini, Groq)

Chat generation can run on a free cloud tier instead of local Ollama, with
no code changes, just a Spring profile. Embeddings **always** stay on Ollama
(`nomic-embed-text`, 768 dims), so the pgvector index and every already
indexed report keep working when you switch.

| Profile | Provider | Default model | Override with | Key from |
|---|---|---|---|---|
| *(none)* | Ollama (local) | `llama3.2` | `OLLAMA_CHAT_MODEL` | none |
| `gemini` | Google Gemini | `gemini-3.8-flash` | `GEMINI_CHAT_MODEL` | [AI Studio](https://aistudio.google.com/apikey) |
| `groq` | Groq | `llama-3.3-70b-versatile` | `GROQ_CHAT_MODEL` | [Groq console](https://console.groq.com/keys) |

Put keys in `backend/.env` (git-ignored, loaded automatically via
`spring.config.import`), e.g. `GEMINI_API_KEY=...`, or set them as environment
variables:

```bash
# PowerShell, with the key in backend/.env
./mvnw spring-boot:run "-Dspring-boot.run.profiles=gemini"

# Git Bash
GROQ_API_KEY=... ./mvnw spring-boot:run -Dspring-boot.run.profiles=groq
```

For the containerized stack, set `SPRING_PROFILES_ACTIVE` plus the matching
key on the backend service.

How it works: both Gemini and Groq expose OpenAI-compatible APIs, so the
profiles use `spring-ai-starter-model-openai` with a different `base-url`.
`spring.ai.model.chat` (`ollama` / `openai`) picks which `ChatModel` is
auto-configured; `spring.ai.model.embedding=ollama` pins embeddings, and the
OpenAI starter's image/audio/moderation models are set to `none` so it never
needs a key in the default profile. The Redis answer cache key includes the
provider, so switching profiles never serves another model's answer.

Caveats:
- **Free tiers may use your prompts for training** (Gemini's does). Only
  use public reports with these profiles.
- **Rate limits are tight**, so pace bulk evaluation runs.
- Free-tier model availability changes. If the default model returns a
  403/404/429, set the `*_CHAT_MODEL` variable to a model the free tier
  currently lists.

## Switching back to Bedrock

Phase 0 originally targeted Amazon Bedrock (Claude for generation, Titan for
embeddings) per the spec, and the project intentionally kept that swap cheap:
replace the `spring-ai-starter-model-ollama` dependency with
`spring-ai-starter-model-bedrock-converse` + `spring-ai-starter-model-bedrock`,
point `spring.ai.bedrock.*` at your AWS region/model, and update
`spring.ai.vectorstore.pgvector.dimensions` to match Titan's embedding size
(1024) instead of `nomic-embed-text`'s 768. No RAG/ingestion/chat code
changes needed: the `ChatModel`/`EmbeddingModel`/`VectorStore` abstractions
are provider-agnostic.

## What a real production deployment would still need

Called out explicitly rather than left implicit — see `deploy/DEPLOYMENT.md`
for the full list: a dedicated VPC (this uses the account's default VPC),
Multi-AZ/autoscaling, a custom domain + ACM certificate, Flyway/Liquibase in
place of `ddl-auto=update`, a real user store in place of the two seeded
demo accounts, and rotating the checked-in dev-only JWT secret.

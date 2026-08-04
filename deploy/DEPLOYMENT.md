# AWS Deployment Architecture

**Status: documented and expressed as Terraform, never applied.** Per a
deliberate scope decision, this project stays free to build and run (no AWS
account was ever needed for Phases 0–4 — Ollama instead of Bedrock, SQS
emulated via LocalStack). This document and the Terraform in
`deploy/terraform/` describe what a real deployment would look like and are
validated with `terraform validate` (syntax/schema against the real AWS
provider), but nothing here has been provisioned or billed. Same honesty
policy as the rest of this repo: a written-but-unrun deployment is labeled
as such, not presented as proven.

## Target architecture

```
                                   Route 53 (optional custom domain)
                                              |
                                        CloudFront
                                   /                    \
                          /* (static)              /api/*, /actuator/*
                              |                             |
                        S3 bucket                Application Load Balancer
                     (Angular build,                        |
                      dist/frontend/browser)          ECS Fargate service
                                                     (backend Docker image,
                                                      from ECR)
                                                              |
                              +-------------------------------+-------------------------------+
                              |                                |                               |
                       RDS PostgreSQL                  ElastiCache Redis                Amazon SQS
                     (pgvector extension,              (embedding + answer               (document-ingestion
                      15.2+, same schema                cache, same code as               queue, same SDK
                      Hibernate already                  Phase 2)                          client as Phase 3 --
                      manages)                                                              just point it at a
                                                                                             real queue URL, no
                                                                                             LocalStack endpoint)

                                                     Amazon Bedrock
                                              (Claude + Titan -- see the
                                               main README's "Switching
                                               back to Bedrock" section;
                                               same ChatModel/EmbeddingModel
                                               abstraction, config-only swap)

                                                   AWS Secrets Manager
                                            (JWT secret, RDS credentials --
                                             injected into the ECS task
                                             definition as secrets, never
                                             plaintext env vars)
```

## What changes from local dev

| Local (this repo, as built) | AWS target |
|---|---|
| Ollama (`llama3.2` + `nomic-embed-text`) | Amazon Bedrock (Claude + Titan) — already a config-only swap, see main README |
| LocalStack SQS | Real Amazon SQS — same `SqsClient` code, just `app.aws.sqs.endpoint` pointed at the real regional endpoint (or unset, letting the SDK use its default) |
| `docker-compose.yml` Postgres+pgvector container | RDS for PostgreSQL 16 (pgvector supported natively from 15.2+, no special parameter group — `CREATE EXTENSION vector` is all it needs, which `spring.ai.vectorstore.pgvector.initialize-schema=true` already runs) |
| `docker-compose.yml` Redis container | ElastiCache for Redis |
| `docker-compose.prod.yml` frontend nginx container | S3 (static hosting) + CloudFront, cheaper and more idiomatic for a static Angular build than running nginx in a container — the containerized option (`frontend/Dockerfile`) still works as an alternative if serving it from ECS is ever preferred |
| GHCR (CI publishing target, see `.github/workflows/ci.yml`) | Amazon ECR |
| `app.jwt.secret` / DB password as plaintext env vars | AWS Secrets Manager, injected as ECS task `secrets` |
| Two seeded demo accounts, `ddl-auto=update` | Both explicitly dev-only already (see main README) — a real deploy needs a real user store and Flyway/Liquibase migrations instead |

## Explicit simplifications in the Terraform

- **Default VPC**, not a dedicated one. A real deployment would isolate RDS
  and ElastiCache in private subnets with no public route; this example
  keeps the networking surface small so the Terraform stays focused on the
  application's own resources (ECS/RDS/Redis/SQS/secrets), which is what
  actually differs from the local setup.
- **No Multi-AZ, no autoscaling policy, no WAF, no custom domain/ACM
  certificate.** All straightforward additions on top of what's here, left
  out to keep the example a clear starting point rather than a fully
  hardened production stack.
- **Single environment.** No Terraform workspaces/state separation for
  staging vs. production.

## Rough cost ballpark

For a low-traffic demo (not a quote — actual pricing varies by region and
changes over time): ECS Fargate task (~0.5 vCPU/1GB) ~$15–30/mo, RDS
`db.t4g.micro` ~$15/mo, ElastiCache `cache.t4g.micro` ~$12/mo, ALB ~$20/mo
plus usage, CloudFront/S3 low-traffic usage a few dollars/mo, SQS/Secrets
Manager negligible at this scale. Roughly **$70–100/month** all-in for a
demo deployment — the reason this project runs everything free/local
instead for a portfolio.

## If this were actually deployed

1. `terraform init && terraform plan` in `deploy/terraform/` against real
   AWS credentials, review the plan, `terraform apply`.
2. Build and push both images to the ECR repositories Terraform creates
   (`docker build`, `docker tag`, `docker push` — or point
   `.github/workflows/ci.yml`'s `publish-images` job at ECR instead of GHCR
   once AWS credentials exist as GitHub secrets).
3. Rotate `app.jwt.secret` to a real random value in Secrets Manager — the
   checked-in default is explicitly dev-only.
4. Replace the two seeded demo accounts with a real user store/registration
   flow before this is reachable by anyone but you.
5. Swap Flyway or Liquibase in for `ddl-auto=update` (see main README's
   "Schema management" design decision).

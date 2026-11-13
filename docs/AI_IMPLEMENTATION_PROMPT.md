# AI Coding Implementation Prompt

Act as a Senior Full-Stack Architect and Engineer. Build Smart Certificate Generator using the requirements in this repository.

## Confirmed stack

Frontend:
- Next.js 16 App Router
- TypeScript strict mode
- Tailwind CSS
- shadcn/ui
- Fabric.js
- Zustand
- React Hook Form and Zod

Backend:
- Java 21
- Spring Boot 3
- Spring Security
- JWT with secure refresh-token rotation
- Spring Data JPA
- PostgreSQL
- Flyway
- Apache PDFBox (subject to Khmer rendering proof of concept)
- ZXing
- Apache POI
- Springdoc OpenAPI

Infrastructure:
- AWS S3
- ECS/Fargate
- RDS PostgreSQL
- CloudWatch
- Secrets Manager

## Product scope

Build an organization-focused certificate platform for administrators and general users. It must support organization branding, reusable templates, a drag-and-drop certificate editor, dynamic fields, individual and bulk issuance, PDF/PNG export, QR verification, revocation, and audit history.

## Engineering rules

1. Read all files in `docs/` before implementation.
2. Follow `AGENTS.md` and `CLAUDE.md`.
3. Use modular monolith architecture.
4. Enforce organization-level authorization on the backend.
5. Use DTOs; never expose persistence entities directly.
6. Use Flyway for schema changes.
7. Validate and version template JSON.
8. Keep PDF rendering independent from frontend canvas internals.
9. Test English and Khmer output before committing to a PDF engine.
10. Use private S3 storage and signed download URLs.
11. Make issuance and batch processing idempotent.
12. Never hard-delete issued certificate records.
13. Add tests for each meaningful feature.
14. Do not claim mock behavior is production functionality.
15. Never commit credentials, tokens, or private keys.

## Required workflow

### First: inspect and plan
- Inspect the repository.
- Identify existing files and avoid overwriting useful work.
- Confirm installed toolchain versions.
- Create/update architecture and implementation notes.
- List technical risks and acceptance criteria.

### Second: technical proof of concept
- Implement a minimal Fabric.js design.
- Serialize/reload the design.
- Render a PDF from the same canonical JSON.
- Test English and Khmer.
- Document the renderer decision.

### Third: implement in phases
1. Foundation and API conventions.
2. Authentication and organization membership.
3. Organization assets and branding.
4. Template CRUD and versioning.
5. Advanced editor.
6. Individual certificate issuance.
7. QR verification and revocation.
8. Bulk processing.
9. Dashboard and audit.
10. Security, testing, and deployment.

After each phase:
- Run formatting, lint, type checks, compilation, and tests.
- Fix regressions before moving on.
- Update README and API documentation.
- Report what is implemented, what is mocked, known issues, and exact commands to run.

## First task

Implement Phase 1 only. Do not start all modules at once. Create the monorepo structure, initialize frontend/backend, configure local PostgreSQL and Flyway, add health checks and Swagger, create the initial dashboard shell, and provide exact local run commands.

Ask before making major architectural changes.

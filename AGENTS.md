# Repository Agent Instructions

## Project context
Smart Certificate Generator is an organization-focused certificate design, issuance, and verification platform.

## Before coding
- Read `README.md` and all relevant files under `docs/`.
- Inspect the current repository before creating or replacing files.
- Follow the implementation roadmap; work one phase at a time.

## Architecture
- Frontend: Next.js App Router + TypeScript strict mode.
- Backend: Java 21 + Spring Boot 3 modular monolith.
- Database: PostgreSQL with Flyway migrations.
- Editor: Fabric.js with canonical, validated, versioned design JSON.
- Storage: private S3 bucket in production.
- PDF and QR generation are backend responsibilities.

## Security
- Enforce RBAC and organization isolation on every backend operation.
- Never trust client-provided organization IDs or roles.
- Never expose JPA entities directly.
- Validate file content, type, size, and dimensions.
- Do not expose private data through public verification.
- Do not commit secrets or real `.env` files.
- Use short-lived signed URLs for private files.

## Code quality
- Keep business logic out of React components and controllers.
- Use DTOs and service boundaries.
- Use clear names and small modules.
- Add tests for new behavior.
- Use consistent error responses and structured logs.
- Avoid unnecessary dependencies and duplicated logic.

## Certificate integrity
- Use immutable template versions for issuance.
- Make issuance idempotent.
- Use unique certificate numbers and high-entropy verification tokens.
- Revoke rather than delete issued certificates.
- Record sensitive actions in audit logs.

## Completion report
For each task, report:
- Files changed.
- Features implemented.
- Commands executed.
- Test/build results.
- Remaining limitations.
- Next recommended task.

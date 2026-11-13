# Implementation Roadmap

Estimated duration: 8–10 weeks for one developer working consistently. Actual duration depends heavily on editor fidelity, Khmer PDF rendering, and bulk-processing requirements.

## Phase 0 — Technical proof of concept (2–3 days)
- Create a small Fabric.js canvas.
- Add text, logo, and QR placeholder.
- Serialize and reload JSON.
- Render the same design to PDF.
- Test English and Khmer fonts.
- Decide PDF rendering approach based on measured output.

**Exit criteria:** Saved design reloads correctly and PDF output is acceptably close to canvas preview.

## Phase 1 — Foundation (Week 1)
- Initialize monorepo.
- Create Next.js and Spring Boot apps.
- Configure PostgreSQL and Flyway.
- Add CI checks and formatting.
- Add common API response/error handling.
- Add OpenAPI.
- Build design system and dashboard shell.

## Phase 2 — Authentication and organization (Week 2)
- Login and refresh flow.
- User membership and role model.
- Organization profile.
- Asset upload.
- Backend organization isolation tests.

## Phase 3 — Template management (Week 3)
- Template CRUD and gallery.
- Template versioning.
- Draft/published/archived lifecycle.
- JSON schema validation.
- Preview endpoint.

## Phase 4 — Advanced editor (Weeks 4–5)
- Fabric.js canvas.
- Text/image/shape/QR elements.
- Drag, resize, rotate, layer ordering.
- Properties panel.
- Undo/redo.
- Autosave and restore.
- Dynamic placeholder insertion.
- Khmer font handling.

## Phase 5 — Certificate issuance (Week 6)
- Number and token generation.
- PDF and PNG renderer.
- Individual issuance workflow.
- Private S3 storage.
- Download authorization.
- Audit events.

## Phase 6 — Bulk and verification (Week 7)
- CSV/XLSX import.
- Column mapping and row validation.
- Async batch processing.
- Retry/idempotency.
- ZIP export.
- Public verification and revocation.

## Phase 7 — Quality and security (Week 8)
- Unit and integration tests.
- E2E user journeys.
- PDF visual regression.
- OWASP review.
- Upload validation.
- Authorization and tenant-isolation tests.
- Performance and error handling.

## Phase 8 — Deployment (Weeks 9–10)
- Production configuration.
- AWS infrastructure.
- Database migration and backup.
- S3 policy and lifecycle.
- HTTPS and domain.
- CI/CD.
- Monitoring and alarms.
- Restore drill and release checklist.

## Definition of done for each phase

- Code compiles and lint/type checks pass.
- Tests for changed behavior pass.
- No secrets committed.
- API and database docs updated.
- Authorization is tested server-side.
- UI includes loading, empty, and error states.
- Work is committed in small, reviewable changes.

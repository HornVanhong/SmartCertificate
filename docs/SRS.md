# Software Requirements Specification (SRS)

## 1. System overview

A browser-based, organization-scoped application composed of a Next.js frontend and Spring Boot REST API. PostgreSQL stores relational data and versioned template JSON. S3 stores uploaded assets and generated documents.

## 2. User roles

| Capability | Admin | General user | Public |
|---|---:|---:|---:|
| View organization dashboard | Yes | Limited | No |
| Manage organization profile | Yes | No | No |
| Manage users and roles | Yes | No | No |
| Create/edit own draft templates | Yes | Permission-based | No |
| Publish global organization templates | Yes | No by default | No |
| Upload assets | Yes | Permission-based | No |
| Issue certificates | Yes | Permission-based | No |
| View all organization certificates | Yes | No by default | No |
| View own/assigned certificates | Yes | Yes | No |
| Revoke certificates | Yes | No by default | No |
| Verify public certificate | Yes | Yes | Yes |

All permissions must be checked by the backend. Frontend route guards are usability features, not security boundaries.

## 3. Functional requirements

### FR-01 Authentication
- Register/invite users, login, logout, refresh session, reset password.
- Secure password hashing using BCrypt or Argon2.
- Secure refresh-token rotation and revocation.
- Account activation/deactivation.

### FR-02 Organization
- Create and update organization profile.
- Store logo, signature, stamp, and brand settings.
- Enforce organization membership and ownership.

### FR-03 Templates
- List, search, filter, create, edit, duplicate, preview, publish, archive.
- Support portrait and landscape page dimensions.
- Store versioned JSON design documents.
- Published template versions must be immutable; edits create a new version.

### FR-04 Editor
- Add/edit/delete/duplicate text, image, shape, logo, signature, stamp, and QR placeholder.
- Drag, resize, rotate, align, reorder layers, lock/unlock.
- Configure font, color, size, alignment, position, dimensions, and visibility.
- Undo/redo, zoom, preview, autosave.
- Support dynamic field tokens.
- Validate design JSON before persistence and rendering.

### FR-05 Generation
- Generate PDF and PNG.
- Assign unique certificate number and cryptographically random verification token.
- Persist issuance metadata and rendered-file reference.
- Make issuance idempotent to prevent duplicate certificates on retries.
- Record actor, template version, issue time, and source batch.

### FR-06 Bulk
- Accept CSV and XLSX within configured size and row limits.
- Map columns to supported fields.
- Validate rows and identify duplicates.
- Preview sample output.
- Process asynchronously.
- Report per-row success/failure.
- Package generated files into ZIP.

### FR-07 Verification
- Public lookup by opaque verification token; optionally support certificate number with rate limiting.
- Return approved public fields only.
- Show VALID, REVOKED, or EXPIRED.
- Record verification events in a privacy-conscious manner.

### FR-08 Certificate management
- Search/filter/paginate.
- View/download.
- Revoke with mandatory reason and audit entry.
- Preserve original issuance record; do not hard-delete issued certificates.

### FR-09 Dashboard
- Counts by status and time period.
- Recent issuance and activity.
- Statistics scoped to organization and user permissions.

### FR-10 Audit
- Record issuance, revocation, template publication, role changes, and organization setting changes.
- Audit records must not be editable by ordinary users.

## 4. Non-functional requirements

- Security: TLS, secure sessions, server-side authorization, input validation, safe file handling.
- Privacy: public verification exposes only configured fields.
- Performance: paginated lists; asynchronous bulk processing; bounded file sizes.
- Reliability: transactional issuance, idempotency, retry-safe jobs.
- Accessibility: keyboard-operable controls, labels, contrast.
- Compatibility: current versions of major desktop browsers; responsive tablet/mobile views.
- Observability: structured logs, health checks, metrics, traceable request IDs.
- Maintainability: modular monolith, DTOs, migrations, tests, API documentation.
- Localization: English and Khmer UI/content support.

## 5. Constraints and assumptions

- Initial release may be deployed for one organization but schema must support multiple organizations.
- S3 bucket remains private.
- PDF renderer must be validated for Khmer shaping and embedded fonts.
- Email delivery and external integrations are future enhancements.

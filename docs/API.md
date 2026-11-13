# REST API Specification

Base path: `/api/v1`  
Content type: `application/json` unless uploading multipart files.

## 1. Response conventions

Success:
```json
{
  "data": {},
  "meta": {
    "requestId": "request-id"
  }
}
```

Error:
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The request contains invalid fields.",
    "fieldErrors": {
      "recipientName": "Recipient name is required."
    },
    "requestId": "request-id"
  }
}
```

Use standard HTTP status codes and never return stack traces or secrets.

## 2. Authentication

| Method | Path | Purpose |
|---|---|---|
| POST | `/auth/register` | Register where self-registration is enabled |
| POST | `/auth/login` | Authenticate |
| POST | `/auth/refresh` | Rotate refresh token |
| POST | `/auth/logout` | Revoke refresh token |
| POST | `/auth/forgot-password` | Start password reset |
| POST | `/auth/reset-password` | Complete password reset |
| GET | `/auth/me` | Current user and memberships |

## 3. Organization and users

| Method | Path | Purpose |
|---|---|---|
| GET | `/organizations/current` | Current organization |
| PUT | `/organizations/current` | Update profile |
| POST | `/organizations/current/assets` | Upload logo/signature/stamp |
| GET | `/organizations/current/assets` | List assets |
| GET | `/users` | List organization users |
| POST | `/users/invitations` | Invite user |
| PATCH | `/users/{id}` | Update permitted user fields |
| PATCH | `/users/{id}/status` | Activate/deactivate |

## 4. Templates

| Method | Path | Purpose |
|---|---|---|
| GET | `/templates` | Search and paginate |
| POST | `/templates` | Create draft |
| GET | `/templates/{id}` | Get template |
| PUT | `/templates/{id}/draft` | Update draft |
| POST | `/templates/{id}/duplicate` | Duplicate |
| POST | `/templates/{id}/preview` | Render preview |
| POST | `/templates/{id}/publish` | Publish version |
| POST | `/templates/{id}/archive` | Archive |

## 5. Certificates

| Method | Path | Purpose |
|---|---|---|
| GET | `/certificates` | Search and paginate |
| POST | `/certificates` | Issue one certificate |
| GET | `/certificates/{id}` | Details |
| GET | `/certificates/{id}/download` | Authorized download |
| POST | `/certificates/{id}/revoke` | Revoke with reason |
| GET | `/certificates/{id}/audit` | Audit history |

Issuance request example:
```json
{
  "templateId": "uuid",
  "recipientName": "Dara Example",
  "recipientEmail": "dara@example.com",
  "courseName": "Cybersecurity Fundamentals",
  "completionDate": "2026-11-01",
  "metadata": {
    "instructorName": "Instructor Name"
  },
  "idempotencyKey": "client-generated-unique-key"
}
```

## 6. Bulk generation

| Method | Path | Purpose |
|---|---|---|
| GET | `/certificate-batches` | Paginated batch list |
| POST | `/certificate-batches/import` | Upload CSV/XLSX |
| GET | `/certificate-batches/{id}` | Status and progress |
| POST | `/certificate-batches/{id}/mapping` | Save column mapping |
| POST | `/certificate-batches/{id}/validate` | Validate rows |
| POST | `/certificate-batches/{id}/generate` | Start processing |
| POST | `/certificate-batches/{id}/retry` | Retry failed items |
| GET | `/certificate-batches/{id}/errors` | Row-level failures |
| GET | `/certificate-batches/{id}/download` | Download ZIP |

Use asynchronous processing and return `202 Accepted` when a batch job is queued.

## 7. Public verification

| Method | Path | Purpose |
|---|---|---|
| GET | `/public/verify/{token}` | Verify opaque token |

Example public response:
```json
{
  "data": {
    "status": "VALID",
    "certificateNumber": "SC-2026-000001",
    "recipientName": "Dara Example",
    "courseName": "Cybersecurity Fundamentals",
    "organizationName": "Example Training Center",
    "issuedAt": "2026-11-12T08:00:00Z"
  }
}
```

Do not expose recipient email, internal IDs, source files, private metadata, or audit details publicly.

## 8. Dashboard and audit logs

| Method | Path | Purpose |
|---|---|---|
| GET | `/dashboard/summary` | Organization summary |
| GET | `/dashboard/issuance-trends` | Time-series counts |
| GET | `/dashboard/recent-activity` | Recent permitted activity |
| GET | `/audit-logs` | Filterable organization audit logs |

## 9. API standards

- Validate request DTOs.
- Paginate list endpoints.
- Use stable error codes.
- Apply authorization and organization scoping.
- Rate-limit authentication and public verification.
- Use idempotency keys for issuance and batch generation.
- Document endpoints using OpenAPI.

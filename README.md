# Smart Certificate Generator

A full-stack certificate design, issuance, management, and verification platform for schools, training centers, and organizations.

## Confirmed project decisions

- Purpose: Organization-focused certificate platform
- Users: Administrators and general users
- Editor: Advanced drag-and-drop editor
- Frontend: Next.js 16 + TypeScript
- Backend: Java 21 + Spring Boot 3
- Database: PostgreSQL
- Editor engine: Fabric.js
- PDF: Apache PDFBox (validate Khmer shaping; evaluate HTML-to-PDF if required)
- QR: ZXing
- Storage: AWS S3
- Deployment target: AWS

## Repository layout

```text
smart-certificate-generator/
├── .github/workflows/        # CI checks (frontend & backend)
├── frontend/                 # Next.js 16 + TypeScript + Tailwind CSS app
├── backend/                  # Java 21 + Spring Boot 3 modular monolith + Flyway
├── docs/                     # Project specifications
├── infrastructure/aws/       # Deployment notes and infrastructure
├── docker-compose.yml        # Local PostgreSQL 16 database
├── .env.example              # Local environment template
├── AGENTS.md
├── CLAUDE.md
└── README.md
```

## Documentation

- [PRD](docs/PRD.md)
- [SRS](docs/SRS.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Database design](docs/DATABASE.md)
- [API specification](docs/API.md)
- [UI/UX guidelines](docs/UI_UX.md)
- [Implementation roadmap](docs/IMPLEMENTATION_ROADMAP.md)
- [Technical decisions and risks](docs/TECHNICAL_DECISIONS.md)
- [AI implementation prompt](docs/AI_IMPLEMENTATION_PROMPT.md)
- [Production operations runbook](docs/PRODUCTION_RUNBOOK.md)
- [Database backup and restore guide](docs/DATABASE_BACKUP_AND_RESTORE.md)

## Getting started (Phase 1 — Foundation)

Phase 1 is initialized with the monorepo layout, PostgreSQL + Flyway schema migrations, Spring Boot REST API conventions + OpenAPI/Swagger, and the Next.js dashboard shell.

### Prerequisites

- Node.js LTS (verified with v24.22.1) and npm
- Docker Desktop (for PostgreSQL 16, or running the backend without local Java 21)
- Java 21 (optional if running the backend via Docker)

### 1. Start PostgreSQL

Copy `.env.example` to `.env` if you want to customize local credentials, then start PostgreSQL:

```bash
docker compose up -d postgres
```

### 2. Run the backend (Spring Boot + Flyway)

**With local Java 21:**

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

On Windows PowerShell:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

**Or with Docker (no local Java 21 required):**

```powershell
docker run --rm -p 8080:8080 --network smart-certificate-generator_default `
  -v "${PWD}/backend:/app" -v smart_cert_m2:/root/.m2 -w /app `
  -e DB_URL=jdbc:postgresql://smart-certificate-postgres:5432/smart_certificate `
  -e DB_USERNAME=smart_cert_user `
  -e DB_PASSWORD=smart_cert_local_pass `
  maven:3.9.9-eclipse-temurin-21 ./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
```

Backend URLs:
- API health check: `http://localhost:8080/api/v1/health`
- Actuator health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### 3. Run the frontend (Next.js)

```bash
cd frontend
npm install
npm run dev
```

Frontend URL:
- Dashboard shell: `http://localhost:3000`

### 4. Verification commands

**Frontend:**

```bash
cd frontend
npm run format:check
npm run lint
npm run typecheck
npm run build
```

**Backend (with local Java 21, or inside `maven:3.9.9-eclipse-temurin-21` container):**

```bash
cd backend
./mvnw -B spotless:check
./mvnw -B test
```

## Product principles

- Organization-scoped data access must be enforced on the backend.
- Certificate issuance must be auditable and transaction-safe.
- Public verification must reveal only explicitly approved fields.
- Templates must be stored as versioned, validated JSON.
- The PDF output must be tested against the editor preview.

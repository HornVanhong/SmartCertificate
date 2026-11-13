# Production Operations Runbook

## 1. Production Topology & Architecture

The Smart Certificate Generator platform is deployed in a secure, multi-AZ AWS architecture:

```
[ Internet ]
      │
      ▼
[ AWS Application Load Balancer (HTTPS / ACM) ]
      ├─ /api/*, /actuator/health* ──► [ ECS Fargate: Spring Boot Backend (Java 21) ]
      │                                       │                         │
      │                                       ▼                         ▼
      │                          [ RDS PostgreSQL 16 ]     [ S3 Private Vault ]
      │                          (Multi-AZ, Encrypted)     (KMS / AES256, TLS Only)
      │
      └─ /* (All other routes) ────────► [ ECS Fargate: Next.js 16 Frontend ]
```

### Component Details
- **Frontend**: Next.js 16 standalone container running unprivileged (`nextjs` UID 1001), serving React Server Components and client bundles.
- **Backend**: Spring Boot 3.4 + Java 21 container running unprivileged (`smartcert` UID 10001), tuned HikariCP connection pooling, native ECS structured JSON logging.
- **Database**: AWS RDS PostgreSQL 16.4 Multi-AZ with automated backups (14-day retention), encrypted storage, and Performance Insights.
- **Storage**: AWS S3 private bucket with Block Public Access, server-side encryption (AES256 or AWS KMS), and lifecycle management (temporary batch exports expire after 14 days; noncurrent versions transition to Glacier after 60 days).
- **Secrets Management**: AWS Secrets Manager with automatic injection into ECS task environments.

---

## 2. Pre-Flight Release Checklist

Complete this checklist prior to promoting any release to production:

- [ ] **CI Quality Gate**: GitHub Actions `ci.yml` passed cleanly:
  - Backend: `spotless:check`, unit tests, integration tests (`./mvnw verify`).
  - Frontend: Prettier formatting, ESLint, TypeScript check, unit tests, and production `next build`.
- [ ] **Database Migrations**:
  - Review all new SQL scripts in `backend/src/main/resources/db/migration/`.
  - Ensure migrations are backward-compatible (non-destructive, default values provided, no long table locks).
- [ ] **Secrets Verification**:
  - Confirm `JWT_SECRET` is at least 64 random characters.
  - Confirm `STORAGE_SIGNED_URL_SECRET` is at least 64 random characters.
  - Confirm `AUTH_COOKIE_SECURE=true` and `CORS_ALLOWED_ORIGINS` points strictly to approved production domains.
- [ ] **Capacity & Budget**:
  - Check AWS Budgets and CloudWatch alarms status.
  - Verify RDS free storage space is above 20 GB.

---

## 3. Zero-Downtime Deployment & Rollback

### Zero-Downtime Rolling Deployment
The ECS services use rolling deployments with:
- `minimum_healthy_percent = 100`
- `maximum_percent = 200`

1. GitHub Actions `.github/workflows/deploy.yml` builds and pushes versioned Docker images to Amazon ECR.
2. ECS starts new container tasks and waits for `/actuator/health/liveness` and `/actuator/health/readiness` (backend) or HTTP 200 (frontend) to pass before routing traffic.
3. Once new tasks are healthy in ALB target groups, old tasks are gracefully drained and stopped.

### Rollback Procedure
If a critical error is detected post-deployment:
1. **Instant Task Rollback via AWS CLI**:
   ```bash
   # Revert backend to previous task definition revision:
   aws ecs update-service \
     --cluster smart-cert-prod-cluster \
     --service smart-cert-prod-backend \
     --task-definition smart-cert-prod-backend:<PREVIOUS_REVISION_NUMBER> \
     --force-new-deployment

   # Revert frontend:
   aws ecs update-service \
     --cluster smart-cert-prod-cluster \
     --service smart-cert-prod-frontend \
     --task-definition smart-cert-prod-frontend:<PREVIOUS_REVISION_NUMBER> \
     --force-new-deployment
   ```
2. **Database Rollback**:
   If a migration was applied that is incompatible with the older release:
   - Refer to [Database Backup and Restore Runbook](DATABASE_BACKUP_AND_RESTORE.md) to restore from the pre-deployment snapshot or apply a compensating Flyway migration script.

---

## 4. Secrets Rotation Procedure

### A. Database Password Rotation
1. Generate a new high-entropy password (32+ characters).
2. In PostgreSQL:
   ```sql
   ALTER USER smart_cert_admin WITH PASSWORD 'new-secure-password';
   ```
3. Update AWS Secrets Manager secret `smart-cert/prod/app-credentials` with the new password.
4. Trigger an ECS rolling restart:
   ```bash
   aws ecs update-service --cluster smart-cert-prod-cluster --service smart-cert-prod-backend --force-new-deployment
   ```

### B. JWT & HMAC Storage Signing Secret Rotation
1. Update `JWT_SECRET` or `STORAGE_SIGNED_URL_SECRET` in AWS Secrets Manager.
2. Note: Rotating `JWT_SECRET` invalidates existing user sessions, requiring users to log in again.
3. Trigger ECS backend rolling restart.

---

## 5. AWS Cost Optimization Guardrails

To prevent unexpected AWS cost spikes:

1. **S3 VPC Endpoint**:
   - `infrastructure/aws/terraform/vpc.tf` provisions a free S3 Gateway Endpoint. All backend S3 traffic bypasses the NAT Gateway, eliminating AWS data processing charges.
2. **S3 Lifecycle Rules**:
   - Temporary batch ZIP export archives (`production/batches/exports/*`) automatically expire and are deleted after 14 days.
   - Noncurrent object versions transition to Glacier Flexible Retrieval after 60 days.
3. **CloudWatch Log Retention**:
   - Both backend and frontend ECS log groups enforce a strict 30-day retention period.
4. **RDS Multi-AZ & Storage Autoscaling**:
   - Initial allocated storage is 50 GB with automatic scaling capped at 200 GB.
5. **ECS Auto-Scaling**:
   - Backend scales down to minimum 2 tasks during low-traffic periods and scales up automatically when CPU exceeds 70%.

---

## 6. Incident Triage Playbook

| Symptom | Probable Cause | Action |
|---------|----------------|--------|
| **ALB 5xx Spike Alarm** | Backend containers crashing, OOM, or database connectivity lost | 1. Check CloudWatch ECS logs `/ecs/smart-cert-prod-backend`.<br>2. Check `/actuator/health` component details.<br>3. Check RDS connection metrics. |
| **High Latency (> 1.5s)** | Slow database queries or unindexed batch lookups | 1. Check RDS Performance Insights for top SQL statements.<br>2. Check parameter group `log_min_duration_statement` logs. |
| **ECS Out-of-Memory (OOM)** | Large PDF generation memory burst | 1. Increase Fargate task memory in Terraform (`backend_memory`).<br>2. Verify Java container flag `-XX:MaxRAMPercentage=75.0`. |
| **Database Low Storage Alarm** | Excessive batch processing or audit events growth | 1. Verify RDS auto-scaling storage.<br>2. Clean up temporary table logs if needed. |

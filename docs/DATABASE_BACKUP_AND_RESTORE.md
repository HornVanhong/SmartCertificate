# Database Backup, Disaster Recovery & Restore Drill Guide

## 1. Disaster Recovery Objectives

- **Recovery Point Objective (RPO)**: < 1 hour (continuous WAL archiving via RDS Automated Backups; point-in-time recovery up to the last 5 minutes).
- **Recovery Time Objective (RTO)**: < 30 minutes (automated restore to a new RDS instance or local container).

---

## 2. Backup Mechanisms

### A. AWS RDS Automated Backups & Snapshots
- **Automated Daily Backups**: Executed daily between `18:00-19:00 UTC` (01:00-02:00 AM ICT) with 14-day retention.
- **Transaction Log (WAL) Archival**: Streamed continuously to S3 for Point-In-Time Recovery (PITR) to any second within the retention window.
- **Pre-Deployment Manual Snapshots**: Created before every major release:
  ```bash
  aws rds create-db-snapshot \
    --db-instance-identifier smart-cert-prod-postgres \
    --db-snapshot-identifier "smart-cert-pre-release-$(date +%Y%m%d-%H%M)"
  ```

### B. Logical Backups via Scripts
- Scheduled via cron or Jenkins/GitHub Actions using `infrastructure/scripts/backup-db.sh` or `backup-db.ps1`.
- Generates compressed binary custom format (`pg_dump -F c -b`) and a SHA-256 checksum file.
- Encrypted with AES-256 and uploaded to an isolated backup S3 bucket with Object Lock / versioning.

---

## 3. Restore Drill Runbook (Quarterly Validation)

Execute this drill in a staging or isolated environment every 90 days to verify backup integrity.

### Step 1: Point-in-Time Recovery via AWS CLI
```bash
# Restore production DB to 1 hour ago into an isolated test instance:
RESTORE_TIME=$(date -u -d '1 hour ago' +"%Y-%m-%dT%H:%M:%SZ")

aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier smart-cert-prod-postgres \
  --target-db-instance-identifier smart-cert-restore-drill \
  --restore-time "${RESTORE_TIME}" \
  --db-subnet-group-name smart-cert-prod-db-subnet-group \
  --vpc-security-group-ids sg-xxxxxxxxx \
  --no-multi-az \
  --no-publicly-accessible
```

### Step 2: Restore from Logical Backup File
Using PowerShell on local/staging:
```powershell
.\infrastructure\scripts\restore-db.ps1 -BackupFile .\backups\smart_certificate_backup_20261113_061517.dump
```
Using Bash:
```bash
./infrastructure/scripts/restore-db.sh ./backups/smart_certificate_backup_20261113_061517.dump
```

### Step 3: Integrity & Schema Verification
Run the following SQL sanity checks on the restored database:

```sql
-- 1. Verify Flyway migration history is intact
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC;

-- 2. Verify organizations and admin accounts exist
SELECT id, name, created_at FROM organizations;
SELECT id, email, role, active FROM users;

-- 3. Verify templates and published versions exist
SELECT id, name, status, current_version, published_version FROM templates;

-- 4. Verify certificates and audit log counts
SELECT COUNT(*) AS total_certificates FROM certificates;
SELECT COUNT(*) AS total_audit_events FROM audit_events;
```

### Step 4: Cleanup Drill Resources
```bash
aws rds delete-db-instance \
  --db-instance-identifier smart-cert-restore-drill \
  --skip-final-snapshot
```
Record the drill date, duration, and operator in the team compliance log.

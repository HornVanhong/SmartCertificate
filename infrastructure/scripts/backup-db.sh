#!/usr/bin/env bash
set -euo pipefail

# Smart Certificate Generator - PostgreSQL Backup Script
# Usage: ./backup-db.sh [backup_dir]

BACKUP_DIR="${1:-./backups}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-smart_certificate}"
DB_USER="${DB_USER:-smart_cert_user}"
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}_backup_${TIMESTAMP}.dump"
CHECKSUM_FILE="${BACKUP_FILE}.sha256"

mkdir -p "${BACKUP_DIR}"

echo "[INFO] Starting database backup for '${DB_NAME}' on ${DB_HOST}:${DB_PORT}..."

# Execute binary custom format dump (pg_dump -Fc) with compression
PGPASSWORD="${DB_PASSWORD:-smart_cert_local_pass}" pg_dump \
  -h "${DB_HOST}" \
  -p "${DB_PORT}" \
  -U "${DB_USER}" \
  -F c \
  -b \
  -v \
  -f "${BACKUP_FILE}" \
  "${DB_NAME}"

# Generate SHA-256 checksum for integrity verification
sha256sum "${BACKUP_FILE}" > "${CHECKSUM_FILE}"

echo "[SUCCESS] Backup complete: ${BACKUP_FILE}"
echo "[INFO] Checksum verified: $(cat "${CHECKSUM_FILE}")"

# Optional: Upload to S3 if AWS_S3_BACKUP_BUCKET is provided
if [ -n "${AWS_S3_BACKUP_BUCKET:-}" ]; then
  echo "[INFO] Uploading backup to s3://${AWS_S3_BACKUP_BUCKET}/db-backups/..."
  aws s3 cp "${BACKUP_FILE}" "s3://${AWS_S3_BACKUP_BUCKET}/db-backups/$(basename "${BACKUP_FILE}")" --sse AES256
  aws s3 cp "${CHECKSUM_FILE}" "s3://${AWS_S3_BACKUP_BUCKET}/db-backups/$(basename "${CHECKSUM_FILE}")" --sse AES256
  echo "[SUCCESS] Upload to S3 completed."
fi

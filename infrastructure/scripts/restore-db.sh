#!/usr/bin/env bash
set -euo pipefail

# Smart Certificate Generator - PostgreSQL Restore Script
# Usage: ./restore-db.sh <backup_dump_file>

if [ "$#" -lt 1 ]; then
  echo "Usage: $0 <path_to_backup_dump_file>"
  exit 1
fi

BACKUP_FILE="$1"
CHECKSUM_FILE="${BACKUP_FILE}.sha256"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-smart_certificate}"
DB_USER="${DB_USER:-smart_cert_user}"

if [ ! -f "${BACKUP_FILE}" ]; then
  echo "[ERROR] Backup file '${BACKUP_FILE}' does not exist!"
  exit 1
fi

# Verify checksum if present
if [ -f "${CHECKSUM_FILE}" ]; then
  echo "[INFO] Verifying SHA-256 checksum..."
  sha256sum -c "${CHECKSUM_FILE}"
else
  echo "[WARN] No checksum file found at '${CHECKSUM_FILE}'. Proceeding with caution."
fi

echo "=========================================================="
echo "WARNING: This will restore database '${DB_NAME}' on ${DB_HOST}:${DB_PORT}."
echo "Current data will be overwritten."
echo "=========================================================="
read -p "Type 'RESTORE' to continue: " CONFIRMATION

if [ "${CONFIRMATION}" != "RESTORE" ]; then
  echo "[CANCELLED] Aborting restore."
  exit 1
fi

echo "[INFO] Executing pg_restore..."
PGPASSWORD="${DB_PASSWORD:-smart_cert_local_pass}" pg_restore \
  -h "${DB_HOST}" \
  -p "${DB_PORT}" \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  --clean \
  --if-exists \
  --no-owner \
  --no-privileges \
  -v \
  "${BACKUP_FILE}"

echo "[SUCCESS] Restore finished. Verifying Flyway migration history..."
PGPASSWORD="${DB_PASSWORD:-smart_cert_local_pass}" psql \
  -h "${DB_HOST}" \
  -p "${DB_PORT}" \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"

echo "[SUCCESS] Database restore and verification completed successfully."

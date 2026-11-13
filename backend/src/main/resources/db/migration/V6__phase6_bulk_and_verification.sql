-- V6__phase6_bulk_and_verification.sql
-- Phase 6: Bulk CSV/XLSX batch tracking enhancements and idempotency indexing.

ALTER TABLE certificate_batches
    ADD COLUMN template_version_id UUID NULL REFERENCES template_versions (id),
    ADD COLUMN source_file_name VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN source_headers JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN valid_records INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN invalid_records INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN idempotency_key VARCHAR(160) NULL,
    ADD COLUMN error_summary TEXT NULL;

CREATE UNIQUE INDEX uq_certificate_batches_org_idempotency
    ON certificate_batches (organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_certificate_batches_org_creator_created_at
    ON certificate_batches (organization_id, created_by, created_at DESC);

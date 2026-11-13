-- V5__phase5_certificate_issuance.sql
-- Phase 5: Certificate issuance metadata, revocation fields, atomic number sequence, and indexes.

ALTER TABLE certificates
    ADD COLUMN completion_date DATE NULL,
    ADD COLUMN revoked_at TIMESTAMPTZ NULL,
    ADD COLUMN revoked_by UUID NULL REFERENCES users (id),
    ADD COLUMN revoked_reason TEXT NULL;

CREATE SEQUENCE IF NOT EXISTS certificate_number_seq START WITH 1 INCREMENT BY 1;

CREATE INDEX idx_certificates_org_number
    ON certificates (organization_id, certificate_number);

CREATE INDEX idx_certificates_org_creator_issued
    ON certificates (organization_id, created_by, issued_at DESC);

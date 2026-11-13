-- V1__init_schema.sql
-- Initial multi-organization schema for Smart Certificate Generator.
-- All primary keys are UUIDs and all timestamps are UTC TIMESTAMPTZ.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(160) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'INVITED', 'LOCKED'))
);

CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email));

CREATE TABLE organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    logo_asset_id UUID NULL,
    contact_email VARCHAR(254),
    phone VARCHAR(50),
    address TEXT,
    website TEXT,
    brand_settings JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_organizations_slug UNIQUE (slug)
);

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    is_system BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uq_roles_system_name ON roles (name) WHERE organization_id IS NULL;
CREATE UNIQUE INDEX uq_roles_org_name ON roles (organization_id, name) WHERE organization_id IS NOT NULL;

CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(120) NOT NULL,
    description TEXT,
    CONSTRAINT uq_permissions_code UNIQUE (code)
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    organization_id UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles (id),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_organizations_membership UNIQUE (user_id, organization_id),
    CONSTRAINT chk_user_organizations_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'INVITED'))
);

CREATE INDEX idx_user_organizations_org_id ON user_organizations (organization_id);

CREATE TABLE media_assets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    object_key TEXT NOT NULL,
    original_file_name TEXT NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    file_size BIGINT NOT NULL,
    checksum VARCHAR(128) NOT NULL,
    uploaded_by UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_media_assets_object_key UNIQUE (object_key),
    CONSTRAINT chk_media_assets_file_size CHECK (file_size >= 0)
);

CREATE INDEX idx_media_assets_org_id ON media_assets (organization_id);

ALTER TABLE organizations
    ADD CONSTRAINT fk_organizations_logo_asset
    FOREIGN KEY (logo_asset_id) REFERENCES media_assets (id) ON DELETE SET NULL;

CREATE TABLE templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name VARCHAR(180) NOT NULL,
    description TEXT,
    category VARCHAR(80),
    orientation VARCHAR(20) NOT NULL,
    page_width NUMERIC(10, 2) NOT NULL,
    page_height NUMERIC(10, 2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    current_version INTEGER NOT NULL DEFAULT 1,
    created_by UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_templates_orientation CHECK (orientation IN ('LANDSCAPE', 'PORTRAIT')),
    CONSTRAINT chk_templates_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT chk_templates_dimensions CHECK (page_width > 0 AND page_height > 0),
    CONSTRAINT chk_templates_current_version CHECK (current_version >= 1)
);

CREATE INDEX idx_templates_org_status ON templates (organization_id, status);

CREATE TABLE template_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES templates (id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    design_json JSONB NOT NULL,
    preview_asset_id UUID NULL REFERENCES media_assets (id) ON DELETE SET NULL,
    created_by UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_template_versions_template_version UNIQUE (template_id, version_number),
    CONSTRAINT chk_template_versions_number CHECK (version_number >= 1)
);

CREATE TABLE certificates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    template_id UUID NOT NULL REFERENCES templates (id),
    template_version_id UUID NOT NULL REFERENCES template_versions (id),
    recipient_name VARCHAR(200) NOT NULL,
    recipient_email VARCHAR(254) NULL,
    course_name VARCHAR(200) NOT NULL,
    certificate_number VARCHAR(100) NOT NULL,
    verification_token_hash VARCHAR(128) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    issued_at TIMESTAMPTZ NULL,
    expires_at TIMESTAMPTZ NULL,
    pdf_object_key TEXT NULL,
    png_object_key TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    idempotency_key VARCHAR(160) NULL,
    created_by UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_certificates_number UNIQUE (certificate_number),
    CONSTRAINT uq_certificates_token_hash UNIQUE (verification_token_hash),
    CONSTRAINT chk_certificates_status CHECK (status IN ('PENDING', 'ISSUED', 'REVOKED', 'EXPIRED', 'FAILED'))
);

CREATE UNIQUE INDEX uq_certificates_org_idempotency
    ON certificates (organization_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_certificates_org_issued_at ON certificates (organization_id, issued_at DESC);
CREATE INDEX idx_certificates_org_status ON certificates (organization_id, status);
CREATE INDEX idx_certificates_org_recipient_name ON certificates (organization_id, LOWER(recipient_name));

CREATE TABLE certificate_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    template_id UUID NOT NULL REFERENCES templates (id),
    status VARCHAR(30) NOT NULL DEFAULT 'UPLOADED',
    total_records INTEGER NOT NULL DEFAULT 0,
    successful_records INTEGER NOT NULL DEFAULT 0,
    failed_records INTEGER NOT NULL DEFAULT 0,
    source_file_asset_id UUID NOT NULL REFERENCES media_assets (id),
    result_zip_object_key TEXT NULL,
    column_mapping JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ NULL,
    CONSTRAINT chk_certificate_batches_status CHECK (
        status IN ('UPLOADED', 'MAPPED', 'VALIDATED', 'QUEUED', 'PROCESSING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED')
    )
);

CREATE INDEX idx_certificate_batches_org_created_at ON certificate_batches (organization_id, created_at DESC);

CREATE TABLE certificate_batch_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id UUID NOT NULL REFERENCES certificate_batches (id) ON DELETE CASCADE,
    row_number INTEGER NOT NULL,
    input_data JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    error_message TEXT NULL,
    certificate_id UUID NULL REFERENCES certificates (id),
    idempotency_key VARCHAR(160) NOT NULL,
    CONSTRAINT uq_batch_items_batch_row UNIQUE (batch_id, row_number),
    CONSTRAINT chk_batch_items_status CHECK (status IN ('PENDING', 'VALID', 'INVALID', 'PROCESSING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_batch_items_batch_status ON certificate_batch_items (batch_id, status);

CREATE TABLE certificate_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    certificate_id UUID NULL REFERENCES certificates (id),
    actor_user_id UUID NULL REFERENCES users (id),
    action VARCHAR(80) NOT NULL,
    reason TEXT NULL,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_org_created_at ON certificate_audit_logs (organization_id, created_at DESC);
CREATE INDEX idx_audit_logs_certificate_id ON certificate_audit_logs (certificate_id) WHERE certificate_id IS NOT NULL;

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);

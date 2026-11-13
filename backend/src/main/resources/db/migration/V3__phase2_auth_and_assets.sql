-- V3__phase2_auth_and_assets.sql
-- Phase 2 additions: asset metadata for branding/signatures/stamps,
-- password reset tokens, and organization user invitations.

ALTER TABLE media_assets
    ADD COLUMN asset_type VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN width INTEGER NULL,
    ADD COLUMN height INTEGER NULL,
    ADD CONSTRAINT chk_media_assets_asset_type CHECK (asset_type IN ('LOGO', 'SIGNATURE', 'STAMP', 'GENERAL')),
    ADD CONSTRAINT chk_media_assets_dimensions CHECK (
        (width IS NULL AND height IS NULL) OR (width > 0 AND height > 0)
    );

CREATE INDEX idx_media_assets_org_type ON media_assets (organization_id, asset_type);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);

CREATE TABLE user_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    email VARCHAR(254) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    role_id UUID NOT NULL REFERENCES roles (id),
    token_hash TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    invited_by UUID NOT NULL REFERENCES users (id),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_user_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_user_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED'))
);

CREATE INDEX idx_user_invitations_org_status ON user_invitations (organization_id, status);
CREATE UNIQUE INDEX uq_user_invitations_pending_org_email
    ON user_invitations (organization_id, LOWER(email))
    WHERE status = 'PENDING';

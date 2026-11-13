-- V4__phase3_template_lifecycle.sql
-- Phase 3 additions: track latest published version separately from current draft version
-- and index template names/categories for organization-scoped gallery search.

ALTER TABLE templates
    ADD COLUMN published_version INTEGER NULL,
    ADD CONSTRAINT chk_templates_published_version CHECK (
        published_version IS NULL OR (published_version >= 1 AND published_version <= current_version)
    );

CREATE INDEX idx_templates_org_name ON templates (organization_id, LOWER(name));
CREATE INDEX idx_templates_org_category ON templates (organization_id, category);

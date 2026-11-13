-- V2__seed_system_roles_and_permissions.sql
-- Baseline system roles and permission codes for organization RBAC.

INSERT INTO permissions (code, description) VALUES
    ('org:read', 'View organization profile and settings'),
    ('org:update', 'Update organization profile and branding'),
    ('org:assets:upload', 'Upload organization logos, signatures, and stamps'),
    ('user:read', 'View organization users and memberships'),
    ('user:manage', 'Invite, update roles, and activate or deactivate organization users'),
    ('template:read', 'View and preview organization templates'),
    ('template:write', 'Create, edit, and duplicate draft templates'),
    ('template:publish', 'Publish and archive organization templates'),
    ('certificate:read', 'View issued certificates and download permitted files'),
    ('certificate:read_all', 'View all organization certificates'),
    ('certificate:issue', 'Issue individual and bulk certificates'),
    ('certificate:revoke', 'Revoke issued certificates'),
    ('audit:read', 'View organization audit logs');

INSERT INTO roles (organization_id, name, is_system) VALUES
    (NULL, 'ADMIN', TRUE),
    (NULL, 'GENERAL_USER', TRUE);

-- ADMIN receives all system permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN' AND r.is_system = TRUE;

-- GENERAL_USER receives baseline read/use permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code IN (
    'org:read',
    'template:read',
    'certificate:read',
    'certificate:issue'
)
WHERE r.name = 'GENERAL_USER' AND r.is_system = TRUE;

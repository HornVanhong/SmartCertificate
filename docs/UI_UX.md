# UI/UX Guidelines

## 1. Visual direction

Modern Professional SaaS with a premium certificate aesthetic.

### Design tokens

| Token | Value |
|---|---|
| Navy | `#142D4E` |
| Gold | `#C6A45B` |
| Blue | `#2563EB` |
| Background | `#F8FAFC` |
| Surface | `#FFFFFF` |
| Border | `#E2E8F0` |
| Main text | `#0F172A` |
| Secondary text | `#64748B` |

Typography:
- UI: Inter or a similar readable sans-serif.
- Khmer UI and certificate content: Noto Sans Khmer or another tested Khmer-capable font.
- Embed licensed font files in PDF output where permitted.

## 2. Application navigation

```text
Smart Certificate
├── Dashboard
├── Certificate Studio
│   ├── Template Gallery
│   ├── My Designs
│   └── New Certificate
├── Certificates
│   ├── All Certificates
│   ├── Bulk Generation
│   └── Revoked
├── Organization
│   ├── Profile
│   ├── Branding & Logo
│   └── Signatures
├── Users & Roles
├── Activity Logs
└── Settings
```

## 3. Required pages

- Login and registration/invitation acceptance.
- Dashboard.
- Template gallery with filters and preview.
- Template create/edit.
- Certificate Studio.
- Individual issuance form.
- Bulk upload, mapping, validation, progress, and result pages.
- Certificate list and detail.
- Organization profile and branding.
- User and role management.
- Audit log.
- Public verification page.

## 4. Editor layout

- Top toolbar: template name, save state, undo, redo, zoom, preview, export.
- Left panel: text, images, shapes, logo, signature, stamp, QR, asset library, layers.
- Center: canvas with rulers/guides and zoom controls.
- Right panel: selected element properties.
- Bottom/status area: page dimensions, zoom, save status.

## 5. UX states

Every data-driven page must include:
- Loading state.
- Empty state with a clear next action.
- Error state with retry.
- Success feedback.
- Disabled state while processing.
- Confirmation for destructive or irreversible actions.

## 6. Accessibility and responsiveness

- Keyboard-accessible controls.
- Visible focus state.
- Proper labels and accessible names.
- Sufficient color contrast.
- Responsive dashboard and forms.
- Editor optimized for desktop; mobile should offer viewing and simple edits if full drag-and-drop is impractical.

## 7. Certificate design guidance

- Support landscape and portrait.
- Keep safe margins.
- Use consistent page dimensions.
- Provide preview at actual aspect ratio.
- Avoid font substitution between editor and PDF.
- Keep QR code large enough to scan after printing.

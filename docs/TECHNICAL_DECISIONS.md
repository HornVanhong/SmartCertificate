# Technical Decisions and Risks

## Decisions

1. **Modular monolith:** simpler deployment and development than microservices for the first release.
2. **Fabric.js:** suitable for interactive canvas editing; isolate it behind editor services and adapters.
3. **Canonical design JSON:** store normalized, versioned design data, not only Fabric's raw serialized state.
4. **PDF renderer:** begin with PDFBox only after a proof of concept; compare with a browser-based HTML-to-PDF renderer if font shaping or layout parity is insufficient.
5. **Private S3:** store object keys and use short-lived signed URLs.
6. **Asynchronous batches:** bulk generation must not run inside a normal HTTP request.
7. **Multi-organization-ready schema:** organization ownership and membership must be explicit from the start.
8. **Immutable issuance history:** revoke rather than delete issued certificates.

## High-priority risks

### R1: Fabric.js and PDF output mismatch
Canvas rendering and PDF rendering use different text engines and coordinate systems.

Mitigation:
- Define a canonical design schema.
- Build the renderer proof of concept before completing the editor.
- Use fixed page dimensions and unit conversion.
- Test font metrics and line wrapping.
- Add PDF visual regression tests.

### R2: Khmer text shaping
A font containing Khmer glyphs does not automatically guarantee correct shaping in every PDF library.

Mitigation:
- Test actual Khmer words and diacritics.
- Embed appropriate licensed fonts.
- Verify shaping, line breaks, and glyph placement.
- Select a renderer that supports the required shaping behavior.

### R3: Cross-organization data leakage
A missing organization filter could expose another organization's records.

Mitigation:
- Resolve organization membership on the backend.
- Scope every repository query.
- Add negative authorization tests.
- Consider PostgreSQL RLS as additional defense.

### R4: Duplicate issuance
Network retries or batch retries could create duplicate certificates.

Mitigation:
- Idempotency keys.
- Unique constraints.
- Transactional state transitions.
- Retry-safe batch item processing.

### R5: Unsafe uploads
Malicious or malformed files may compromise processing or consume resources.

Mitigation:
- MIME sniffing and extension allowlists.
- File size and image dimension limits.
- SVG sanitization or rasterization.
- Malware scanning where appropriate.
- Store uploads outside the web root in private storage.

### R6: Cost growth
Managed AWS services and data transfer can be expensive.

Mitigation:
- Set AWS Budgets and billing alerts.
- Estimate storage and compute use.
- Avoid unnecessary NAT gateways.
- Configure S3 lifecycle policies.
- Start with a small deployment footprint.

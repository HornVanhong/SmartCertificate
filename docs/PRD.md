# Product Requirements Document (PRD)

## 1. Product summary

**Product name:** Smart Certificate Generator  
**Tagline:** Design. Generate. Verify.

Smart Certificate Generator is a web application that enables organizations to create branded certificate templates, design certificates visually, issue certificates individually or in bulk, and let recipients verify authenticity online.

## 2. Problem statement

Organizations often create certificates manually using graphic-design software and spreadsheets. This process is time-consuming, difficult to standardize, prone to data-entry errors, and lacks a reliable public verification mechanism.

## 3. Goals

1. Reduce the time required to design and issue certificates.
2. Provide reusable, organization-branded templates.
3. Enable non-designers to customize certificate layouts.
4. Support individual and bulk issuance.
5. Provide tamper-resistant verification identifiers and QR codes.
6. Maintain issuance, revocation, and administrative audit history.
7. Support English and Khmer content.

## 4. Non-goals for the first release

- Blockchain-based credentials.
- Paid marketplace and payment processing.
- AI-generated designs.
- Native mobile applications.
- Legally binding qualified digital signatures.
- Complex external student-information-system integrations.

## 5. Target users

### Administrator
Manages organization settings, users, permissions, templates, assets, certificate issuance, revocation, and audit records.

### General user
Uses authorized templates, creates designs where permitted, issues certificates within granted permissions, and views their own or assigned records.

### Public verifier
An unauthenticated visitor who scans a QR code or enters a certificate number to check a certificate's status.

## 6. Core user journeys

### Journey A: Set up an organization
1. Administrator signs in.
2. Completes organization profile.
3. Uploads logo, signatures, and stamp assets.
4. Configures brand colors and default information.
5. Invites or creates general users.

### Journey B: Create a template
1. User opens Template Gallery.
2. Selects a template or starts from blank.
3. Opens Certificate Studio.
4. Adds and positions text, images, shapes, logo, signature, and QR placeholder.
5. Configures dynamic fields.
6. Saves a draft and previews it.
7. Authorized administrator publishes the template.

### Journey C: Issue one certificate
1. User selects a published template.
2. Enters recipient and course details.
3. Previews the result.
4. Confirms issuance.
5. System assigns unique certificate number and verification token.
6. System renders and stores the PDF.
7. User downloads or shares the certificate.

### Journey D: Bulk issue
1. User uploads CSV/XLSX.
2. Selects a template.
3. Maps file columns to dynamic fields.
4. Validates all rows and previews samples.
5. Confirms the batch.
6. Background worker generates certificates.
7. User downloads a ZIP and reviews failed rows.

### Journey E: Verify a certificate
1. Recipient scans QR or opens verification URL.
2. Public page looks up the unguessable verification token or certificate number.
3. Page displays approved public fields and VALID / REVOKED / EXPIRED status.
4. Page does not expose private organization or recipient information.

## 7. Functional scope

- Authentication and role-based authorization.
- Organization profile and branding.
- User and membership management.
- Template gallery and lifecycle.
- Advanced visual editor.
- Dynamic placeholder engine.
- Individual PDF/PNG generation.
- Bulk CSV/XLSX generation.
- QR-based public verification.
- Certificate search, download, and revocation.
- Dashboard analytics.
- Audit logging.
- Asset storage.

## 8. Success metrics

- A trained user can issue one certificate in under 2 minutes after selecting a template.
- A batch of 100 valid rows completes with visible progress and row-level error reporting.
- Every issued certificate has a unique ID and verification token.
- Verification page returns a result in a target of under 2 seconds under normal load.
- No cross-organization data access in authorization tests.
- PDF output passes visual checks for supported English and Khmer sample content.

## 9. MVP acceptance criteria

- Admin can configure organization profile and upload a logo.
- Admin can create, save, publish, and archive templates.
- Authorized user can edit template elements using drag-and-drop.
- Design can be saved and reopened without layout loss.
- System can issue an individual PDF with QR code.
- Public verification accurately reflects certificate status.
- Bulk import validates rows and reports failures.
- Revocation is auditable and immediately reflected in verification.
- English and Khmer output is tested using embedded fonts and a validated rendering approach.

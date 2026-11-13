import assert from "node:assert/strict";
import test from "node:test";
import {
  extractVerificationToken,
  formatAuditActionLabel,
  summarizeAuditDetails,
} from "./token-utils.ts";

test("extractVerificationToken extracts opaque token from full verification URLs and relative paths", () => {
  assert.equal(extractVerificationToken("  abc123_token  "), "abc123_token");
  assert.equal(
    extractVerificationToken("https://verify.smartcert.test/verify?token=qr_tok_987&lang=km"),
    "qr_tok_987"
  );
  assert.equal(extractVerificationToken("/verify?token=%20signedToken42%20"), "signedToken42");
  assert.equal(extractVerificationToken("   "), "");
});

test("formatAuditActionLabel formats known and custom audit actions consistently", () => {
  assert.equal(formatAuditActionLabel("CERTIFICATE_ISSUED"), "Certificate Issued");
  assert.equal(formatAuditActionLabel("CERTIFICATE_REVOKED"), "Certificate Revoked");
  assert.equal(
    formatAuditActionLabel("BATCH_COMPLETED_WITH_ERRORS"),
    "Batch Completed With Errors"
  );
  assert.equal(formatAuditActionLabel(""), "Activity");
});

test("summarizeAuditDetails formats up to three non-empty metadata fields", () => {
  assert.equal(summarizeAuditDetails(null), "");
  assert.equal(summarizeAuditDetails({ empty: "", skipped: null }), "");
  assert.equal(
    summarizeAuditDetails({
      recipientName: "Sok Dara",
      certificateNumber: "CERT-2026-001",
      format: "PDF",
      ignored: "fourth",
    }),
    "recipientName: Sok Dara · certificateNumber: CERT-2026-001 · format: PDF"
  );
});

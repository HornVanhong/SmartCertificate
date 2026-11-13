"use client";

import * as React from "react";
import { Loader2, ShieldAlert, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { revokeCertificate } from "@/lib/api/certificates";
import { ApiClientError, type CertificateDetail } from "@/lib/api/types";

interface RevokeCertificateModalProps {
  certificate: {
    id: string;
    certificateNumber: string;
    recipientName: string;
  } | null;
  onClose: () => void;
  onRevoked: (updated: CertificateDetail) => void;
}

export function RevokeCertificateModal({
  certificate,
  onClose,
  onRevoked,
}: RevokeCertificateModalProps) {
  const [reason, setReason] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);
  const [submitting, setSubmitting] = React.useState(false);

  React.useEffect(() => {
    if (!certificate) return;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !submitting) {
        onClose();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [certificate, onClose, submitting]);

  if (!certificate) {
    return null;
  }

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmed = reason.trim();
    if (trimmed.length < 3) {
      setError("Please enter a revocation reason (at least 3 characters).");
      return;
    }

    setSubmitting(true);
    setError(null);
    try {
      const response = await revokeCertificate(certificate.id, trimmed);
      setReason("");
      onRevoked(response.data);
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : "Failed to revoke certificate.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4 backdrop-blur-xs"
      role="dialog"
      aria-modal="true"
      aria-labelledby="revoke-certificate-title"
    >
      <div className="w-full max-w-lg rounded-xl border border-border-subtle bg-surface p-6 shadow-xl">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-rose-50 text-rose-600">
              <ShieldAlert className="h-5 w-5" />
            </div>
            <div>
              <h2 id="revoke-certificate-title" className="text-lg font-semibold text-text-main">
                Revoke Certificate
              </h2>
              <p className="mt-1 text-sm text-text-secondary">
                Certificate{" "}
                <span className="font-mono font-medium text-text-main">
                  {certificate.certificateNumber}
                </span>{" "}
                for <span className="font-medium text-text-main">{certificate.recipientName}</span>{" "}
                will be marked revoked permanently and recorded in the audit trail.
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            className="rounded-lg p-1.5 text-text-secondary transition-colors hover:bg-slate-100 hover:text-text-main disabled:opacity-50"
            aria-label="Close modal"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="mt-5 space-y-4">
          <div>
            <label
              htmlFor="revocation-reason-input"
              className="mb-1.5 block text-sm font-medium text-text-main"
            >
              Revocation Reason <span className="text-rose-600">*</span>
            </label>
            <textarea
              id="revocation-reason-input"
              rows={3}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              placeholder="Explain why this certificate is being revoked (e.g., data entry correction, academic misconduct, superseded record)."
              className="w-full rounded-lg border border-border-subtle bg-white px-3 py-2 text-sm text-text-main shadow-xs focus:border-brand-navy focus:outline-none focus:ring-2 focus:ring-brand-navy/15"
              maxLength={1000}
              disabled={submitting}
            />
            <p className="mt-1 text-xs text-text-secondary">
              This reason is preserved in the audit log and surfaced on verification checks.
            </p>
          </div>

          {error ? (
            <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
              {error}
            </p>
          ) : null}

          <div className="flex items-center justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={onClose} disabled={submitting}>
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              className="bg-rose-600 text-white hover:bg-rose-700"
              disabled={submitting}
            >
              {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
              Confirm Revocation
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}

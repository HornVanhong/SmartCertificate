"use client";

import * as React from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import {
  ArrowLeft,
  Calendar,
  Clock,
  Download,
  FileText,
  Image as ImageIcon,
  Loader2,
  Mail,
  ShieldAlert,
  UserCheck,
} from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { RequireAuthPanel } from "@/components/auth/require-auth-panel";
import { CertificateStatusBadge } from "@/components/certificates/certificate-status-badge";
import { RevokeCertificateModal } from "@/components/certificates/revoke-certificate-modal";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { StatePanel } from "@/components/ui/state-panel";
import {
  downloadCertificateBlob,
  getCertificate,
  listCertificateAuditLogs,
  triggerBrowserBlobDownload,
} from "@/lib/api/certificates";
import { getTemplate } from "@/lib/api/templates";
import {
  ApiClientError,
  type CertificateAuditLog,
  type CertificateDetail,
  type CertificateFormat,
} from "@/lib/api/types";

function formatTimestamp(value: string | null | undefined, includeTime = true): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return includeTime
    ? date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })
    : date.toLocaleDateString(undefined, { dateStyle: "medium" });
}

function formatAuditAction(action: string): {
  label: string;
  variant: "navy" | "danger" | "neutral";
} {
  switch (action) {
    case "CERTIFICATE_ISSUED":
      return { label: "Issued", variant: "navy" };
    case "CERTIFICATE_REVOKED":
      return { label: "Revoked", variant: "danger" };
    default:
      return { label: action, variant: "neutral" };
  }
}

export default function CertificateDetailPage() {
  const params = useParams<{ id: string }>();
  const certificateId = Array.isArray(params.id) ? params.id[0] : params.id;
  const { status: authStatus, hasPermission } = useAuth();
  const canRead = hasPermission("certificate:read") || hasPermission("certificate:read_all");
  const canRevoke = hasPermission("certificate:revoke");

  const [certificate, setCertificate] = React.useState<CertificateDetail | null>(null);
  const [templateName, setTemplateName] = React.useState<string | null>(null);
  const [auditLogs, setAuditLogs] = React.useState<CertificateAuditLog[]>([]);
  const [previewUrl, setPreviewUrl] = React.useState<string | null>(null);
  const [previewError, setPreviewError] = React.useState<string | null>(null);

  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [reloadTick, setReloadTick] = React.useState(0);

  const [downloadingFormat, setDownloadingFormat] = React.useState<CertificateFormat | null>(null);
  const [actionError, setActionError] = React.useState<string | null>(null);
  const [revokeModalOpen, setRevokeModalOpen] = React.useState(false);

  React.useEffect(() => {
    if (authStatus !== "authenticated" || !canRead || !certificateId) return;

    const controller = new AbortController();
    let active = true;

    async function loadDetails() {
      setLoading(true);
      setError(null);
      try {
        const [certResponse, auditResponse] = await Promise.all([
          getCertificate(certificateId, controller.signal),
          listCertificateAuditLogs(certificateId, { page: 0, size: 50 }, controller.signal).catch(
            () => ({ data: [] as CertificateAuditLog[] })
          ),
        ]);
        if (!active) return;
        setCertificate(certResponse.data);
        setAuditLogs(auditResponse.data);

        getTemplate(certResponse.data.templateId, controller.signal)
          .then((tplResponse) => {
            if (active) {
              setTemplateName(tplResponse.data.name);
            }
          })
          .catch(() => {
            if (active) {
              setTemplateName(null);
            }
          });
      } catch (err) {
        if (!active || controller.signal.aborted) return;
        setError(err instanceof ApiClientError ? err.message : "Failed to load certificate.");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void loadDetails();
    return () => {
      active = false;
      controller.abort();
    };
  }, [authStatus, canRead, certificateId, reloadTick]);

  React.useEffect(() => {
    if (authStatus !== "authenticated" || !canRead || !certificate?.pngAvailable) {
      return;
    }

    const controller = new AbortController();
    let active = true;
    let allocatedUrl: string | null = null;

    async function loadPreviewImage() {
      setPreviewError(null);
      try {
        const blob = await downloadCertificateBlob(certificate!.id, "PNG", true, controller.signal);
        if (!active) return;
        allocatedUrl = URL.createObjectURL(blob);
        setPreviewUrl(allocatedUrl);
      } catch (err) {
        if (!active || controller.signal.aborted) return;
        setPreviewError(
          err instanceof ApiClientError
            ? err.message
            : "Unable to render authenticated certificate preview."
        );
      }
    }

    void loadPreviewImage();
    return () => {
      active = false;
      controller.abort();
      if (allocatedUrl) {
        URL.revokeObjectURL(allocatedUrl);
      }
    };
  }, [authStatus, canRead, certificate]);

  if (authStatus === "loading") {
    return (
      <StatePanel
        variant="loading"
        title="Checking session"
        description="Verifying your organization permissions..."
      />
    );
  }

  if (authStatus !== "authenticated") {
    return <RequireAuthPanel>{null}</RequireAuthPanel>;
  }

  if (!canRead) {
    return (
      <StatePanel
        variant="error"
        title="Access Restricted"
        description="Your role does not include permission to inspect issued certificates."
      />
    );
  }

  if (loading && !certificate) {
    return (
      <StatePanel
        variant="loading"
        title="Loading certificate"
        description="Fetching certificate details, artifacts, and audit history..."
      />
    );
  }

  if (error || !certificate) {
    return (
      <StatePanel
        variant="error"
        title="Unable to load certificate"
        description={error ?? "Certificate not found."}
        actionLabel="Retry"
        onAction={() => setReloadTick((value) => value + 1)}
      />
    );
  }

  const handleDownload = async (format: CertificateFormat) => {
    setDownloadingFormat(format);
    setActionError(null);
    try {
      const blob = await downloadCertificateBlob(certificate.id, format, false);
      const ext = format === "PDF" ? "pdf" : "png";
      triggerBrowserBlobDownload(blob, `${certificate.certificateNumber}.${ext}`);
    } catch (err) {
      setActionError(
        err instanceof ApiClientError ? err.message : `Failed to download ${format} artifact.`
      );
    } finally {
      setDownloadingFormat(null);
    }
  };

  const metadataEntries = Object.entries(certificate.metadata ?? {});

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <Link
            href="/certificates"
            className="inline-flex items-center gap-1.5 text-xs font-medium text-text-secondary hover:text-brand-navy"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            Back to Certificates
          </Link>
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="font-mono text-2xl font-semibold tracking-tight text-text-main">
              {certificate.certificateNumber}
            </h1>
            <CertificateStatusBadge status={certificate.status} />
          </div>
          <p className="text-sm text-text-secondary">
            Issued to{" "}
            <span className="font-medium text-text-main">{certificate.recipientName}</span> for{" "}
            <span className="font-medium text-text-main">{certificate.courseName}</span>
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button
            type="button"
            variant="primary"
            disabled={downloadingFormat !== null || !certificate.pdfAvailable}
            onClick={() => handleDownload("PDF")}
          >
            {downloadingFormat === "PDF" ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Download className="h-4 w-4" />
            )}
            Download PDF
          </Button>
          <Button
            type="button"
            variant="outline"
            disabled={downloadingFormat !== null || !certificate.pngAvailable}
            onClick={() => handleDownload("PNG")}
          >
            {downloadingFormat === "PNG" ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <ImageIcon className="h-4 w-4" />
            )}
            Download PNG
          </Button>
          {canRevoke && certificate.status !== "REVOKED" ? (
            <Button
              type="button"
              variant="primary"
              className="bg-rose-600 text-white hover:bg-rose-700"
              onClick={() => setRevokeModalOpen(true)}
            >
              <ShieldAlert className="h-4 w-4" />
              Revoke Certificate
            </Button>
          ) : null}
        </div>
      </div>

      {actionError ? (
        <div
          role="alert"
          className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
        >
          {actionError}
        </div>
      ) : null}

      {certificate.status === "REVOKED" ? (
        <Card className="border-rose-200 bg-rose-50/50">
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-base text-rose-800">
              <ShieldAlert className="h-4 w-4" />
              Certificate Revoked
            </CardTitle>
            <CardDescription className="text-rose-700">
              Revoked on {formatTimestamp(certificate.revokedAt)}
              {certificate.revokedReason ? ` — Reason: ${certificate.revokedReason}` : ""}
            </CardDescription>
          </CardHeader>
        </Card>
      ) : null}

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1.25fr)_1fr]">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Rendered Certificate Preview</CardTitle>
            <CardDescription>
              High-resolution 150 DPI raster preview served from private backend storage.
            </CardDescription>
          </CardHeader>
          <CardContent>
            {previewError ? (
              <StatePanel
                variant="error"
                title="Preview unavailable"
                description={previewError}
                actionLabel="Reload Preview"
                onAction={() => setReloadTick((value) => value + 1)}
              />
            ) : previewUrl ? (
              <div className="overflow-hidden rounded-lg border border-border-subtle bg-slate-100 p-3">
                {/* Blob object URLs from authenticated backend responses cannot use next/image optimization */}
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={previewUrl}
                  alt={`Rendered certificate ${certificate.certificateNumber}`}
                  className="mx-auto max-h-[640px] w-auto rounded shadow-md"
                />
              </div>
            ) : (
              <StatePanel
                variant="loading"
                title="Rendering preview"
                description="Downloading authenticated PNG preview..."
              />
            )}
          </CardContent>
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Certificate Record</CardTitle>
              <CardDescription>Immutable issuance metadata & artifact state.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3 text-sm">
              <div className="flex items-start gap-2.5">
                <UserCheck className="mt-0.5 h-4 w-4 shrink-0 text-text-secondary" />
                <div>
                  <div className="text-xs text-text-secondary">Recipient</div>
                  <div className="font-medium text-text-main">{certificate.recipientName}</div>
                </div>
              </div>

              <div className="flex items-start gap-2.5">
                <Mail className="mt-0.5 h-4 w-4 shrink-0 text-text-secondary" />
                <div>
                  <div className="text-xs text-text-secondary">Recipient Email</div>
                  <div className="font-medium text-text-main">
                    {certificate.recipientEmail ?? "—"}
                  </div>
                </div>
              </div>

              <div className="flex items-start gap-2.5">
                <FileText className="mt-0.5 h-4 w-4 shrink-0 text-text-secondary" />
                <div>
                  <div className="text-xs text-text-secondary">Template</div>
                  <div className="font-medium text-text-main">
                    {templateName ?? certificate.templateId}
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3 border-t border-border-subtle pt-3">
                <div className="flex items-start gap-2">
                  <Calendar className="mt-0.5 h-4 w-4 shrink-0 text-text-secondary" />
                  <div>
                    <div className="text-xs text-text-secondary">Completion Date</div>
                    <div className="font-medium text-text-main">
                      {formatTimestamp(certificate.completionDate, false)}
                    </div>
                  </div>
                </div>
                <div className="flex items-start gap-2">
                  <Clock className="mt-0.5 h-4 w-4 shrink-0 text-text-secondary" />
                  <div>
                    <div className="text-xs text-text-secondary">Issued At</div>
                    <div className="font-medium text-text-main">
                      {formatTimestamp(certificate.issuedAt)}
                    </div>
                  </div>
                </div>
              </div>

              {certificate.expiresAt ? (
                <div className="border-t border-border-subtle pt-3">
                  <div className="text-xs text-text-secondary">Expires At</div>
                  <div className="font-medium text-text-main">
                    {formatTimestamp(certificate.expiresAt)}
                  </div>
                </div>
              ) : null}

              {metadataEntries.length > 0 ? (
                <div className="border-t border-border-subtle pt-3">
                  <div className="mb-2 text-xs font-semibold tracking-wide text-text-secondary uppercase">
                    Custom Metadata
                  </div>
                  <dl className="space-y-1.5">
                    {metadataEntries.map(([key, value]) => (
                      <div key={key} className="flex items-center justify-between gap-2 text-xs">
                        <dt className="font-mono text-text-secondary">{key}</dt>
                        <dd className="font-medium text-text-main">{value}</dd>
                      </div>
                    ))}
                  </dl>
                </div>
              ) : null}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">Audit History</CardTitle>
              <CardDescription>
                Chronological compliance events recorded for this certificate.
              </CardDescription>
            </CardHeader>
            <CardContent>
              {auditLogs.length === 0 ? (
                <p className="text-sm text-text-secondary">No audit events recorded.</p>
              ) : (
                <ol className="space-y-4">
                  {auditLogs.map((entry) => {
                    const actionMeta = formatAuditAction(entry.action);
                    return (
                      <li
                        key={entry.id}
                        className="rounded-lg border border-border-subtle bg-slate-50/60 p-3 text-sm"
                      >
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <Badge variant={actionMeta.variant}>{actionMeta.label}</Badge>
                          <span className="text-xs text-text-secondary">
                            {formatTimestamp(entry.createdAt)}
                          </span>
                        </div>
                        {entry.reason ? (
                          <p className="mt-2 text-xs text-text-main">Reason: {entry.reason}</p>
                        ) : null}
                        {entry.actorUserId ? (
                          <p className="mt-1 font-mono text-[11px] text-text-secondary">
                            Actor: {entry.actorUserId}
                          </p>
                        ) : null}
                      </li>
                    );
                  })}
                </ol>
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      <RevokeCertificateModal
        certificate={revokeModalOpen ? certificate : null}
        onClose={() => setRevokeModalOpen(false)}
        onRevoked={(updated) => {
          setRevokeModalOpen(false);
          setCertificate(updated);
          setReloadTick((value) => value + 1);
        }}
      />
    </div>
  );
}

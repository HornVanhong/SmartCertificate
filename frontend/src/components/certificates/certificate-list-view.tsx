"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  Download,
  ExternalLink,
  FilePlus2,
  Image as ImageIcon,
  Loader2,
  Search,
  ShieldAlert,
} from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { RequireAuthPanel } from "@/components/auth/require-auth-panel";
import { CertificateStatusBadge } from "@/components/certificates/certificate-status-badge";
import { RevokeCertificateModal } from "@/components/certificates/revoke-certificate-modal";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { StatePanel } from "@/components/ui/state-panel";
import {
  downloadCertificateBlob,
  listCertificates,
  triggerBrowserBlobDownload,
} from "@/lib/api/certificates";
import { listTemplates } from "@/lib/api/templates";
import {
  ApiClientError,
  type CertificateFormat,
  type CertificateStatus,
  type CertificateSummary,
  type PageMeta,
  type TemplateSummary,
} from "@/lib/api/types";

interface CertificateListViewProps {
  mode: "all" | "revoked";
}

function formatDate(value: string | null | undefined, includeTime = false): string {
  if (!value) return "—";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return includeTime
    ? parsed.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })
    : parsed.toLocaleDateString(undefined, { dateStyle: "medium" });
}

export function CertificateListView({ mode }: CertificateListViewProps) {
  const router = useRouter();
  const { status: authStatus, hasPermission } = useAuth();
  const canRead = hasPermission("certificate:read") || hasPermission("certificate:read_all");
  const canReadAll = hasPermission("certificate:read_all");
  const canIssue = hasPermission("certificate:issue");
  const canRevoke = hasPermission("certificate:revoke");

  const [queryInput, setQueryInput] = React.useState("");
  const [appliedQuery, setAppliedQuery] = React.useState("");
  const [statusFilter, setStatusFilter] = React.useState<CertificateStatus | "">(
    mode === "revoked" ? "REVOKED" : ""
  );
  const [templateFilter, setTemplateFilter] = React.useState("");
  const [page, setPage] = React.useState(0);
  const [refreshTick, setRefreshTick] = React.useState(0);

  const [certificates, setCertificates] = React.useState<CertificateSummary[]>([]);
  const [templates, setTemplates] = React.useState<TemplateSummary[]>([]);
  const [pagination, setPagination] = React.useState<PageMeta | null>(null);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [actionError, setActionError] = React.useState<string | null>(null);
  const [downloadingKey, setDownloadingKey] = React.useState<string | null>(null);
  const [revokeTarget, setRevokeTarget] = React.useState<CertificateSummary | null>(null);

  React.useEffect(() => {
    if (authStatus !== "authenticated" || !canRead) return;
    const controller = new AbortController();

    listTemplates({ size: 100 }, controller.signal)
      .then((response) => setTemplates(response.data))
      .catch(() => {
        if (!controller.signal.aborted) {
          setTemplates([]);
        }
      });

    return () => controller.abort();
  }, [authStatus, canRead]);

  React.useEffect(() => {
    if (authStatus !== "authenticated" || !canRead) return;

    const controller = new AbortController();
    let active = true;

    async function load() {
      setLoading(true);
      setError(null);
      try {
        const effectiveStatus = mode === "revoked" ? "REVOKED" : statusFilter;
        const response = await listCertificates(
          {
            q: appliedQuery,
            status: effectiveStatus,
            templateId: templateFilter,
            page,
            size: 12,
          },
          controller.signal
        );
        if (!active) return;
        setCertificates(response.data);
        setPagination(response.meta.pagination ?? null);
      } catch (err) {
        if (!active || controller.signal.aborted) return;
        setError(err instanceof ApiClientError ? err.message : "Failed to load certificates.");
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void load();
    return () => {
      active = false;
      controller.abort();
    };
  }, [authStatus, canRead, mode, appliedQuery, statusFilter, templateFilter, page, refreshTick]);

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

  const templateNameMap = new Map(templates.map((tpl) => [tpl.id, tpl.name]));

  const handleDownload = async (certificate: CertificateSummary, format: CertificateFormat) => {
    const key = `${certificate.id}:${format}`;
    setDownloadingKey(key);
    setActionError(null);
    try {
      const blob = await downloadCertificateBlob(certificate.id, format, false);
      const ext = format === "PDF" ? "pdf" : "png";
      triggerBrowserBlobDownload(blob, `${certificate.certificateNumber}.${ext}`);
    } catch (err) {
      setActionError(
        err instanceof ApiClientError ? err.message : `Failed to download ${format} file.`
      );
    } finally {
      setDownloadingKey(null);
    }
  };

  const handleSearchSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setPage(0);
    setAppliedQuery(queryInput.trim());
  };

  const title = mode === "revoked" ? "Revoked Certificates" : "All Certificates";
  const description =
    mode === "revoked"
      ? "Review permanently revoked certificates, revocation timestamps, and compliance audit trails."
      : canReadAll
        ? "Search, inspect, download, and manage certificates issued across your organization."
        : "Search, inspect, and download certificates issued by your account.";

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight text-text-main">{title}</h1>
            <Badge variant={canReadAll ? "navy" : "blue"}>
              {canReadAll ? "Organization Scope" : "My Issued Certificates"}
            </Badge>
          </div>
          <p className="mt-1 text-sm text-text-secondary">{description}</p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {mode === "revoked" ? (
            <Link href="/certificates">
              <Button variant="outline">View All Certificates</Button>
            </Link>
          ) : (
            <Link href="/certificates/revoked">
              <Button variant="outline">
                <ShieldAlert className="h-4 w-4" />
                Revoked Registry
              </Button>
            </Link>
          )}
          {canIssue ? (
            <Link href="/studio/new">
              <Button variant="primary">
                <FilePlus2 className="h-4 w-4" />
                Issue Certificate
              </Button>
            </Link>
          ) : null}
        </div>
      </div>

      <Card>
        <CardHeader className="pb-4">
          <CardTitle className="text-base">Filter Certificates</CardTitle>
          <CardDescription>
            Search by recipient name, email, course title, or unique certificate number (`SC-...`).
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form
            onSubmit={handleSearchSubmit}
            className="grid gap-3 md:grid-cols-[1fr_auto_auto_auto]"
          >
            <div className="relative">
              <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-text-secondary" />
              <Input
                value={queryInput}
                onChange={(event) => setQueryInput(event.target.value)}
                placeholder="Search recipient, course, email, or SC-2026-000001..."
                className="pl-9"
              />
            </div>

            {mode === "all" ? (
              <select
                value={statusFilter}
                onChange={(event) => {
                  setPage(0);
                  setStatusFilter(event.target.value as CertificateStatus | "");
                }}
                aria-label="Filter by status"
                className="h-10 rounded-lg border border-border-subtle bg-white px-3 text-sm text-text-main shadow-xs focus:border-brand-navy focus:outline-none"
              >
                <option value="">All Statuses</option>
                <option value="ISSUED">Issued</option>
                <option value="REVOKED">Revoked</option>
                <option value="EXPIRED">Expired</option>
                <option value="PENDING">Pending</option>
                <option value="FAILED">Failed</option>
              </select>
            ) : null}

            <select
              value={templateFilter}
              onChange={(event) => {
                setPage(0);
                setTemplateFilter(event.target.value);
              }}
              aria-label="Filter by template"
              className="h-10 max-w-[220px] rounded-lg border border-border-subtle bg-white px-3 text-sm text-text-main shadow-xs focus:border-brand-navy focus:outline-none"
            >
              <option value="">All Templates</option>
              {templates.map((template) => (
                <option key={template.id} value={template.id}>
                  {template.name}
                </option>
              ))}
            </select>

            <div className="flex items-center gap-2">
              <Button type="submit" variant="primary">
                Search
              </Button>
              {appliedQuery || statusFilter || templateFilter ? (
                <Button
                  type="button"
                  variant="ghost"
                  onClick={() => {
                    setQueryInput("");
                    setAppliedQuery("");
                    setStatusFilter(mode === "revoked" ? "REVOKED" : "");
                    setTemplateFilter("");
                    setPage(0);
                  }}
                >
                  Reset
                </Button>
              ) : null}
            </div>
          </form>
        </CardContent>
      </Card>

      {actionError ? (
        <div
          role="alert"
          className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
        >
          {actionError}
        </div>
      ) : null}

      {loading ? (
        <StatePanel
          variant="loading"
          title="Loading certificates"
          description="Fetching issued certificate records from your organization vault..."
        />
      ) : error ? (
        <StatePanel
          variant="error"
          title="Unable to load certificates"
          description={error}
          actionLabel="Retry"
          onAction={() => setRefreshTick((value) => value + 1)}
        />
      ) : certificates.length === 0 ? (
        <StatePanel
          variant="empty"
          title={
            mode === "revoked" ? "No revoked certificates found" : "No certificates match filters"
          }
          description={
            mode === "revoked"
              ? "No certificates in your visible scope have been revoked."
              : "Issue an individual certificate from a published template or adjust your search filters."
          }
          actionLabel={canIssue && mode === "all" ? "Issue New Certificate" : undefined}
          onAction={canIssue && mode === "all" ? () => router.push("/studio/new") : undefined}
        />
      ) : (
        <Card>
          <CardContent className="p-0">
            <div className="overflow-x-auto">
              <table className="w-full min-w-[820px] border-collapse text-left text-sm">
                <thead>
                  <tr className="border-b border-border-subtle bg-slate-50/80 text-xs font-semibold tracking-wide text-text-secondary uppercase">
                    <th className="px-4 py-3">Certificate #</th>
                    <th className="px-4 py-3">Recipient</th>
                    <th className="px-4 py-3">Course / Program</th>
                    <th className="px-4 py-3">Template</th>
                    <th className="px-4 py-3">Issued</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-subtle">
                  {certificates.map((cert) => {
                    const pdfBusy = downloadingKey === `${cert.id}:PDF`;
                    const pngBusy = downloadingKey === `${cert.id}:PNG`;
                    return (
                      <tr key={cert.id} className="transition-colors hover:bg-slate-50/60">
                        <td className="px-4 py-3 font-mono text-xs font-semibold text-brand-navy">
                          <Link
                            href={`/certificates/${cert.id}`}
                            className="hover:text-brand-blue hover:underline"
                          >
                            {cert.certificateNumber}
                          </Link>
                        </td>
                        <td className="px-4 py-3">
                          <div className="font-medium text-text-main">{cert.recipientName}</div>
                          {cert.recipientEmail ? (
                            <div className="text-xs text-text-secondary">{cert.recipientEmail}</div>
                          ) : null}
                        </td>
                        <td className="px-4 py-3 text-text-main">
                          <div>{cert.courseName}</div>
                          {cert.completionDate ? (
                            <div className="text-xs text-text-secondary">
                              Completed {formatDate(cert.completionDate)}
                            </div>
                          ) : null}
                        </td>
                        <td className="px-4 py-3 text-xs text-text-secondary">
                          {templateNameMap.get(cert.templateId) ?? "Template"}
                        </td>
                        <td className="px-4 py-3 text-xs text-text-secondary">
                          <div>{formatDate(cert.issuedAt, true)}</div>
                          {cert.revokedAt ? (
                            <div className="text-rose-700">
                              Revoked {formatDate(cert.revokedAt, true)}
                            </div>
                          ) : null}
                        </td>
                        <td className="px-4 py-3">
                          <CertificateStatusBadge status={cert.status} />
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex items-center justify-end gap-1.5">
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() => handleDownload(cert, "PDF")}
                              disabled={pdfBusy || pngBusy}
                              title="Download PDF"
                            >
                              {pdfBusy ? (
                                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                              ) : (
                                <Download className="h-3.5 w-3.5" />
                              )}
                              PDF
                            </Button>
                            <Button
                              type="button"
                              variant="outline"
                              size="sm"
                              onClick={() => handleDownload(cert, "PNG")}
                              disabled={pdfBusy || pngBusy}
                              title="Download PNG"
                            >
                              {pngBusy ? (
                                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                              ) : (
                                <ImageIcon className="h-3.5 w-3.5" />
                              )}
                              PNG
                            </Button>
                            {canRevoke && cert.status !== "REVOKED" ? (
                              <Button
                                type="button"
                                variant="ghost"
                                size="sm"
                                className="text-rose-700 hover:bg-rose-50 hover:text-rose-800"
                                onClick={() => setRevokeTarget(cert)}
                                title="Revoke Certificate"
                              >
                                <ShieldAlert className="h-3.5 w-3.5" />
                              </Button>
                            ) : null}
                            <Link href={`/certificates/${cert.id}`}>
                              <Button type="button" variant="ghost" size="sm" title="Inspect">
                                <ExternalLink className="h-3.5 w-3.5" />
                              </Button>
                            </Link>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}

      {pagination && pagination.totalPages > 1 ? (
        <div className="flex items-center justify-between border-t border-border-subtle pt-4 text-sm">
          <p className="text-text-secondary">
            Page {pagination.page + 1} of {pagination.totalPages} ({pagination.totalElements} total)
          </p>
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={page <= 0}
              onClick={() => setPage((value) => Math.max(0, value - 1))}
            >
              Previous
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={page + 1 >= pagination.totalPages}
              onClick={() => setPage((value) => value + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      ) : null}

      <RevokeCertificateModal
        certificate={revokeTarget}
        onClose={() => setRevokeTarget(null)}
        onRevoked={() => {
          setRevokeTarget(null);
          setRefreshTick((value) => value + 1);
        }}
      />
    </div>
  );
}

"use client";

import * as React from "react";
import Link from "next/link";
import { RefreshCw, Search, ShieldCheck } from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { RequireAuthPanel } from "@/components/auth/require-auth-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { StatePanel } from "@/components/ui/state-panel";
import { listAuditLogs } from "@/lib/api/dashboard";
import { ApiClientError, type CertificateAuditLog } from "@/lib/api/types";
import { formatAuditActionLabel, summarizeAuditDetails } from "@/lib/verification/token-utils";

const ACTION_OPTIONS = [
  { value: "", label: "All Actions" },
  { value: "CERTIFICATE_ISSUED", label: "Certificate Issued" },
  { value: "CERTIFICATE_REVOKED", label: "Certificate Revoked" },
  { value: "CERTIFICATE_DOWNLOADED", label: "Certificate Downloaded" },
  { value: "TEMPLATE_CREATED", label: "Template Created" },
  { value: "TEMPLATE_PUBLISHED", label: "Template Published" },
  { value: "BATCH_COMPLETED", label: "Batch Completed" },
  { value: "USER_INVITED", label: "User Invited" },
] as const;

function formatTimestamp(isoString: string): string {
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return isoString;
  return date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "medium" });
}

function actionBadgeVariant(action: string): "success" | "danger" | "blue" | "neutral" {
  if (action === "CERTIFICATE_ISSUED") return "success";
  if (action === "CERTIFICATE_REVOKED") return "danger";
  if (action === "CERTIFICATE_DOWNLOADED") return "blue";
  return "neutral";
}

function ActivityLogsContent() {
  const { status, hasPermission } = useAuth();
  const canReadAudit = hasPermission("audit:read");

  const [queryInput, setQueryInput] = React.useState("");
  const [appliedQuery, setAppliedQuery] = React.useState("");
  const [actionFilter, setActionFilter] = React.useState("");
  const [page, setPage] = React.useState(0);
  const [refreshTick, setRefreshTick] = React.useState(0);

  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [logs, setLogs] = React.useState<CertificateAuditLog[]>([]);
  const [totalElements, setTotalElements] = React.useState(0);
  const [totalPages, setTotalPages] = React.useState(0);

  React.useEffect(() => {
    if (status !== "authenticated" || !canReadAudit) {
      return;
    }

    const controller = new AbortController();
    let active = true;

    listAuditLogs(
      {
        action: actionFilter,
        q: appliedQuery,
        page,
        size: 20,
      },
      controller.signal
    )
      .then((response) => {
        if (!active) return;
        setLogs(response.data);
        setTotalElements(response.meta.pagination?.totalElements ?? response.data.length);
        setTotalPages(response.meta.pagination?.totalPages ?? 1);
        setError(null);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (!active || controller.signal.aborted) return;
        setError(
          err instanceof ApiClientError
            ? err.message
            : "Unable to load audit logs from the backend service."
        );
        setLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [status, canReadAudit, actionFilter, appliedQuery, page, refreshTick]);

  if (!canReadAudit) {
    return (
      <StatePanel
        variant="empty"
        title="Audit Logs Restricted"
        description="Your organization role does not grant the audit:read permission required to view organization-wide activity logs."
      >
        <Link
          href="/"
          className="inline-flex h-9 items-center justify-center rounded-lg border border-border-subtle bg-surface px-4 text-sm font-medium text-text-main hover:bg-slate-50"
        >
          Return to Dashboard
        </Link>
      </StatePanel>
    );
  }

  const handleSearchSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setPage(0);
    setAppliedQuery(queryInput.trim());
  };

  const handleActionChange = (event: React.ChangeEvent<HTMLSelectElement>) => {
    setLoading(true);
    setError(null);
    setPage(0);
    setActionFilter(event.target.value);
  };

  const handleRefresh = () => {
    setLoading(true);
    setError(null);
    setRefreshTick((tick) => tick + 1);
  };

  const handlePageChange = (nextPage: number) => {
    setLoading(true);
    setError(null);
    setPage(nextPage);
  };

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-4">
          <div className="space-y-1">
            <CardTitle className="flex items-center gap-2 text-lg">
              <ShieldCheck className="h-5 w-5 text-brand-navy" />
              Organization Activity Logs
            </CardTitle>
            <CardDescription>
              Immutable audit trail for credential issuance, downloads, and revocations.
            </CardDescription>
          </div>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleRefresh}
            disabled={loading}
          >
            <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} />
            Refresh
          </Button>
        </CardHeader>
        <CardContent className="space-y-4">
          <form onSubmit={handleSearchSubmit} className="flex flex-col gap-3 sm:flex-row">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-text-secondary" />
              <Input
                value={queryInput}
                onChange={(event) => setQueryInput(event.target.value)}
                placeholder="Search by reason, certificate number, or event details..."
                className="pl-9"
                aria-label="Search audit logs"
              />
            </div>
            <select
              value={actionFilter}
              onChange={handleActionChange}
              aria-label="Filter by audit action"
              className="h-9 rounded-lg border border-border-subtle bg-surface px-3 text-sm text-text-main focus:border-brand-navy focus:outline-none"
            >
              {ACTION_OPTIONS.map((option) => (
                <option key={option.value || "all"} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <Button type="submit" variant="primary" size="sm" className="h-9 px-4">
              Filter
            </Button>
          </form>

          {error ? (
            <StatePanel variant="error" title="Failed to load activity logs" description={error}>
              <Button type="button" variant="outline" size="sm" onClick={handleRefresh}>
                Retry
              </Button>
            </StatePanel>
          ) : loading && logs.length === 0 ? (
            <StatePanel
              variant="loading"
              title="Loading audit trail"
              description="Fetching organization security and issuance events..."
            />
          ) : logs.length === 0 ? (
            <StatePanel
              variant="empty"
              title="No matching audit events"
              description="Try clearing the search query or changing the action filter."
            />
          ) : (
            <div className="overflow-x-auto rounded-lg border border-border-subtle">
              <table className="w-full border-collapse text-left text-sm">
                <thead className="bg-slate-50 text-xs font-semibold uppercase tracking-wide text-text-secondary">
                  <tr>
                    <th className="px-4 py-3">Timestamp</th>
                    <th className="px-4 py-3">Action</th>
                    <th className="px-4 py-3">Summary</th>
                    <th className="px-4 py-3">Details</th>
                    <th className="px-4 py-3">Certificate</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-subtle bg-surface">
                  {logs.map((entry) => {
                    const summary = summarizeAuditDetails(entry.details);
                    return (
                      <tr key={entry.id} className="hover:bg-slate-50/70">
                        <td className="whitespace-nowrap px-4 py-3 text-xs text-text-secondary">
                          {formatTimestamp(entry.createdAt)}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3">
                          <Badge variant={actionBadgeVariant(entry.action)}>
                            {formatAuditActionLabel(entry.action)}
                          </Badge>
                        </td>
                        <td className="px-4 py-3 font-medium text-text-main">
                          {entry.reason || "—"}
                        </td>
                        <td className="max-w-xs truncate px-4 py-3 text-xs text-text-secondary">
                          {summary || "—"}
                        </td>
                        <td className="whitespace-nowrap px-4 py-3 font-mono text-xs">
                          {entry.certificateId ? (
                            <Link
                              href={`/certificates/${encodeURIComponent(entry.certificateId)}`}
                              className="text-brand-blue hover:underline"
                            >
                              {entry.certificateId.slice(0, 8)}...
                            </Link>
                          ) : (
                            <span className="text-text-secondary">—</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}

          {totalPages > 0 ? (
            <div className="flex flex-wrap items-center justify-between gap-3 pt-1 text-xs text-text-secondary">
              <span>
                Page {page + 1} of {Math.max(1, totalPages)} ({totalElements} events)
              </span>
              <div className="flex items-center gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={loading || page <= 0}
                  onClick={() => handlePageChange(page - 1)}
                >
                  Previous
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  disabled={loading || page + 1 >= totalPages}
                  onClick={() => handlePageChange(page + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}

export default function ActivityLogsPage() {
  return (
    <RequireAuthPanel>
      <ActivityLogsContent />
    </RequireAuthPanel>
  );
}

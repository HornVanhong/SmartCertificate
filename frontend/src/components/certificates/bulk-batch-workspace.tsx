"use client";

import * as React from "react";
import Link from "next/link";
import {
  AlertTriangle,
  Archive,
  CheckCircle2,
  Download,
  FileSpreadsheet,
  Loader2,
  Play,
  RefreshCw,
  Upload,
} from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { RequireAuthPanel } from "@/components/auth/require-auth-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { StatePanel } from "@/components/ui/state-panel";
import {
  downloadBatchZipBlob,
  generateBatch,
  getBatch,
  importBatchFile,
  listBatches,
  listBatchErrors,
  retryBatch,
  saveBatchMapping,
  validateBatch,
} from "@/lib/api/batches";
import { triggerBrowserBlobDownload } from "@/lib/api/certificates";
import { listTemplates } from "@/lib/api/templates";
import {
  ApiClientError,
  type BatchDetail,
  type BatchErrorItem,
  type BatchStatus,
  type BatchSummary,
  type TemplateSummary,
} from "@/lib/api/types";

const REQUIRED_FIELDS = new Set(["recipientName", "courseName"]);

const FIELD_LABELS: Record<string, string> = {
  recipientName: "Recipient Full Name",
  courseName: "Course / Program Name",
  recipientEmail: "Recipient Email",
  completionDate: "Completion Date",
  expiresAt: "Expiration Date",
};

function createKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `batch-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "—";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

function BatchStatusBadge({ status }: { status: BatchStatus }) {
  switch (status) {
    case "COMPLETED":
      return <Badge variant="success">Completed</Badge>;
    case "COMPLETED_WITH_ERRORS":
      return <Badge variant="warning">Completed with Errors</Badge>;
    case "FAILED":
      return <Badge variant="danger">Failed</Badge>;
    case "QUEUED":
    case "PROCESSING":
      return <Badge variant="blue">{status === "QUEUED" ? "Queued" : "Processing"}</Badge>;
    case "VALIDATED":
      return <Badge variant="navy">Validated</Badge>;
    case "MAPPED":
      return <Badge variant="gold">Mapped</Badge>;
    default:
      return <Badge variant="neutral">Uploaded</Badge>;
  }
}

export function BulkBatchWorkspace() {
  const { status: authStatus, hasPermission } = useAuth();
  const canIssue = hasPermission("certificate:issue");
  const canRead =
    canIssue || hasPermission("certificate:read") || hasPermission("certificate:read_all");

  const [templates, setTemplates] = React.useState<TemplateSummary[]>([]);
  const [recentBatches, setRecentBatches] = React.useState<BatchSummary[]>([]);
  const [initialLoading, setInitialLoading] = React.useState(false);
  const [initialError, setInitialError] = React.useState<string | null>(null);
  const [refreshTick, setRefreshTick] = React.useState(0);

  const [selectedTemplateId, setSelectedTemplateId] = React.useState("");
  const [selectedFile, setSelectedFile] = React.useState<File | null>(null);
  const [importIdempotencyKey, setImportIdempotencyKey] = React.useState(() => createKey());

  const [activeBatch, setActiveBatch] = React.useState<BatchDetail | null>(null);
  const [draftMapping, setDraftMapping] = React.useState<Record<string, string>>({});
  const [batchErrors, setBatchErrors] = React.useState<BatchErrorItem[]>([]);

  const [busyAction, setBusyAction] = React.useState<string | null>(null);
  const [actionError, setActionError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (authStatus !== "authenticated" || !canRead) return;

    const controller = new AbortController();
    let active = true;

    async function loadInitial() {
      setInitialLoading(true);
      setInitialError(null);
      try {
        const [tplRes, batchesRes] = await Promise.all([
          listTemplates({ size: 100 }, controller.signal),
          listBatches({ page: 0, size: 10 }, controller.signal),
        ]);
        if (!active) return;

        const eligibleTemplates = tplRes.data.filter(
          (tpl) => tpl.publishedVersion !== null && tpl.status !== "ARCHIVED"
        );
        setTemplates(eligibleTemplates);
        setSelectedTemplateId((prev) =>
          prev && eligibleTemplates.some((tpl) => tpl.id === prev)
            ? prev
            : (eligibleTemplates[0]?.id ?? "")
        );
        setRecentBatches(batchesRes.data);
      } catch (err) {
        if (!active || controller.signal.aborted) return;
        setInitialError(
          err instanceof ApiClientError ? err.message : "Failed to load bulk workspace data."
        );
      } finally {
        if (active) {
          setInitialLoading(false);
        }
      }
    }

    void loadInitial();
    return () => {
      active = false;
      controller.abort();
    };
  }, [authStatus, canRead, refreshTick]);

  const activeBatchId = activeBatch?.id ?? null;
  const activeBatchStatus = activeBatch?.status ?? null;

  React.useEffect(() => {
    if (
      authStatus !== "authenticated" ||
      !activeBatchId ||
      (activeBatchStatus !== "QUEUED" && activeBatchStatus !== "PROCESSING")
    ) {
      return;
    }

    let cancelled = false;
    const poll = async () => {
      try {
        const response = await getBatch(activeBatchId);
        if (cancelled) return;
        setActiveBatch(response.data);
        setDraftMapping(response.data.columnMapping ?? {});

        if (response.data.status !== "QUEUED" && response.data.status !== "PROCESSING") {
          const [errorsRes, listRes] = await Promise.all([
            listBatchErrors(activeBatchId, { page: 0, size: 50 }).catch(() => ({
              data: [] as BatchErrorItem[],
            })),
            listBatches({ page: 0, size: 10 }).catch(() => ({ data: [] as BatchSummary[] })),
          ]);
          if (!cancelled) {
            setBatchErrors(errorsRes.data);
            setRecentBatches(listRes.data);
          }
        }
      } catch {
        // Keep interval alive for transient poll hiccups
      }
    };

    const timer = window.setInterval(() => void poll(), 1500);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [authStatus, activeBatchId, activeBatchStatus]);

  const selectExistingBatch = async (batchId: string) => {
    setBusyAction("load");
    setActionError(null);
    try {
      const [detailRes, errorsRes] = await Promise.all([
        getBatch(batchId),
        listBatchErrors(batchId, { page: 0, size: 50 }),
      ]);
      setActiveBatch(detailRes.data);
      setDraftMapping(detailRes.data.columnMapping ?? {});
      setBatchErrors(errorsRes.data);
    } catch (err) {
      setActionError(err instanceof ApiClientError ? err.message : "Failed to load batch details.");
    } finally {
      setBusyAction(null);
    }
  };

  const handleImport = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selectedTemplateId) {
      setActionError("Select a published template before uploading.");
      return;
    }
    if (!selectedFile) {
      setActionError("Choose a .csv or .xlsx roster file to upload.");
      return;
    }

    setBusyAction("import");
    setActionError(null);
    try {
      const response = await importBatchFile({
        templateId: selectedTemplateId,
        file: selectedFile,
        idempotencyKey: importIdempotencyKey,
      });
      setActiveBatch(response.data);
      setDraftMapping(response.data.columnMapping ?? {});
      setBatchErrors([]);
      setSelectedFile(null);
      setImportIdempotencyKey(createKey());

      const listRes = await listBatches({ page: 0, size: 10 });
      setRecentBatches(listRes.data);
    } catch (err) {
      setActionError(err instanceof ApiClientError ? err.message : "Failed to import roster file.");
    } finally {
      setBusyAction(null);
    }
  };

  const handleSaveMapping = async () => {
    if (!activeBatch) return;
    setBusyAction("mapping");
    setActionError(null);
    try {
      const response = await saveBatchMapping(activeBatch.id, draftMapping);
      setActiveBatch(response.data);
      setDraftMapping(response.data.columnMapping ?? {});
      setBatchErrors([]);
    } catch (err) {
      setActionError(
        err instanceof ApiClientError ? err.message : "Failed to save column mapping."
      );
    } finally {
      setBusyAction(null);
    }
  };

  const handleValidate = async () => {
    if (!activeBatch) return;
    setBusyAction("validate");
    setActionError(null);
    try {
      const response = await validateBatch(activeBatch.id);
      setActiveBatch(response.data);
      const errorsRes = await listBatchErrors(activeBatch.id, { page: 0, size: 50 });
      setBatchErrors(errorsRes.data);
    } catch (err) {
      setActionError(err instanceof ApiClientError ? err.message : "Failed to validate rows.");
    } finally {
      setBusyAction(null);
    }
  };

  const handleGenerate = async () => {
    if (!activeBatch) return;
    setBusyAction("generate");
    setActionError(null);
    try {
      const response = await generateBatch(activeBatch.id);
      setActiveBatch(response.data);
    } catch (err) {
      setActionError(
        err instanceof ApiClientError ? err.message : "Failed to queue batch generation."
      );
    } finally {
      setBusyAction(null);
    }
  };

  const handleRetryFailed = async () => {
    if (!activeBatch) return;
    setBusyAction("retry");
    setActionError(null);
    try {
      const response = await retryBatch(activeBatch.id);
      setActiveBatch(response.data);
    } catch (err) {
      setActionError(err instanceof ApiClientError ? err.message : "Failed to retry failed rows.");
    } finally {
      setBusyAction(null);
    }
  };

  const handleDownloadZip = async (batchId: string) => {
    setBusyAction("download");
    setActionError(null);
    try {
      const blob = await downloadBatchZipBlob(batchId);
      triggerBrowserBlobDownload(blob, `certificates-batch-${batchId.slice(0, 8)}.zip`);
    } catch (err) {
      setActionError(err instanceof ApiClientError ? err.message : "Failed to download ZIP.");
    } finally {
      setBusyAction(null);
    }
  };

  const handleDownloadSampleCsv = () => {
    const csv =
      "recipientName,courseName,recipientEmail,completionDate\n" +
      "Sokha Dara,Cloud Security Fundamentals,sokha@example.org,2026-11-10\n" +
      "Alex Rivera,Cloud Security Fundamentals,alex@example.org,2026-11-11\n";
    triggerBrowserBlobDownload(
      new Blob([csv], { type: "text/csv;charset=utf-8" }),
      "sample-roster.csv"
    );
  };

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
        description="Your role does not include permission to access bulk certificate generation."
      />
    );
  }

  const templateMap = new Map(templates.map((tpl) => [tpl.id, tpl.name]));
  const isLockedForEditing =
    activeBatch?.status === "QUEUED" ||
    activeBatch?.status === "PROCESSING" ||
    activeBatch?.status === "COMPLETED" ||
    activeBatch?.status === "COMPLETED_WITH_ERRORS";

  const progressTotal = activeBatch ? Math.max(activeBatch.validRecords, 1) : 1;
  const progressDone = activeBatch ? activeBatch.successfulRecords + activeBatch.failedRecords : 0;
  const progressPercent = Math.min(100, Math.round((progressDone / progressTotal) * 100));

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight text-text-main">
              Bulk Certificate Generation
            </h1>
            <Badge variant="navy">Async CSV / XLSX Pipeline</Badge>
          </div>
          <p className="mt-1 text-sm text-text-secondary">
            Import CSV or XLSX rosters, map columns to template placeholders, validate rows, and
            export signed certificates as a ZIP archive.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <Button type="button" variant="outline" onClick={handleDownloadSampleCsv}>
            <FileSpreadsheet className="h-4 w-4" />
            Sample CSV
          </Button>
          <Link href="/certificates">
            <Button variant="outline">All Certificates</Button>
          </Link>
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

      {initialLoading ? (
        <StatePanel
          variant="loading"
          title="Loading bulk workspace"
          description="Fetching published templates and recent batch jobs..."
        />
      ) : initialError ? (
        <StatePanel
          variant="error"
          title="Unable to load bulk workspace"
          description={initialError}
          actionLabel="Retry"
          onAction={() => setRefreshTick((value) => value + 1)}
        />
      ) : (
        <div className="grid gap-6 lg:grid-cols-[1.25fr_minmax(0,400px)]">
          <div className="space-y-6">
            {canIssue ? (
              <Card>
                <CardHeader>
                  <CardTitle className="flex items-center gap-2 text-base">
                    <Upload className="h-4 w-4 text-brand-navy" />
                    Step 1 · Import Roster File
                  </CardTitle>
                  <CardDescription>
                    Upload a UTF-8 `.csv` or `.xlsx` spreadsheet (up to 5 MB and 500 rows per
                    batch).
                  </CardDescription>
                </CardHeader>
                <CardContent>
                  {templates.length === 0 ? (
                    <StatePanel
                      variant="empty"
                      title="No published templates available"
                      description="Publish a template in Certificate Studio before starting bulk generation."
                    />
                  ) : (
                    <form onSubmit={handleImport} className="space-y-4">
                      <div className="grid gap-4 sm:grid-cols-2">
                        <div>
                          <label
                            htmlFor="bulk-template"
                            className="mb-1.5 block text-sm font-medium text-text-main"
                          >
                            Published Template
                          </label>
                          <select
                            id="bulk-template"
                            value={selectedTemplateId}
                            onChange={(event) => setSelectedTemplateId(event.target.value)}
                            className="h-10 w-full rounded-lg border border-border-subtle bg-white px-3 text-sm text-text-main shadow-xs focus:border-brand-navy focus:outline-none"
                          >
                            {templates.map((tpl) => (
                              <option key={tpl.id} value={tpl.id}>
                                {tpl.name} (v{tpl.publishedVersion})
                              </option>
                            ))}
                          </select>
                        </div>

                        <div>
                          <label
                            htmlFor="bulk-file"
                            className="mb-1.5 block text-sm font-medium text-text-main"
                          >
                            CSV or XLSX File
                          </label>
                          <input
                            id="bulk-file"
                            type="file"
                            accept=".csv,.xlsx"
                            onChange={(event) => setSelectedFile(event.target.files?.[0] ?? null)}
                            className="block h-10 w-full rounded-lg border border-border-subtle bg-white px-3 py-1.5 text-sm text-text-main file:mr-3 file:rounded-md file:border-0 file:bg-slate-100 file:px-2.5 file:py-1 file:text-xs file:font-medium file:text-text-main"
                          />
                        </div>
                      </div>

                      <div className="flex flex-wrap items-center justify-between gap-3">
                        <span className="font-mono text-[11px] text-text-secondary">
                          Idempotency key: {importIdempotencyKey}
                        </span>
                        <Button
                          type="submit"
                          variant="primary"
                          disabled={busyAction !== null || !selectedFile}
                        >
                          {busyAction === "import" ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : (
                            <Upload className="h-4 w-4" />
                          )}
                          Upload & Parse Roster
                        </Button>
                      </div>
                    </form>
                  )}
                </CardContent>
              </Card>
            ) : null}

            {activeBatch ? (
              <>
                <Card>
                  <CardHeader>
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div>
                        <CardTitle className="text-base">
                          Step 2 · Column Mapping — {activeBatch.sourceFileName}
                        </CardTitle>
                        <CardDescription>
                          {activeBatch.totalRecords} data rows detected across{" "}
                          {activeBatch.sourceHeaders.length} columns.
                        </CardDescription>
                      </div>
                      <BatchStatusBadge status={activeBatch.status} />
                    </div>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="grid gap-3 sm:grid-cols-2">
                      {activeBatch.templatePlaceholders.map((field) => {
                        const isRequired = REQUIRED_FIELDS.has(field);
                        return (
                          <div key={field} className="space-y-1">
                            <label
                              htmlFor={`map-${field}`}
                              className="block text-xs font-medium text-text-main"
                            >
                              {FIELD_LABELS[field] ?? field}{" "}
                              {isRequired ? <span className="text-rose-600">*</span> : null}
                            </label>
                            <select
                              id={`map-${field}`}
                              disabled={isLockedForEditing || !canIssue}
                              value={draftMapping[field] ?? ""}
                              onChange={(event) =>
                                setDraftMapping((prev) => ({
                                  ...prev,
                                  [field]: event.target.value,
                                }))
                              }
                              className="h-9 w-full rounded-lg border border-border-subtle bg-white px-2.5 text-sm text-text-main disabled:bg-slate-100"
                            >
                              <option value="">— Not Mapped —</option>
                              {activeBatch.sourceHeaders.map((header) => (
                                <option key={header} value={header}>
                                  {header}
                                </option>
                              ))}
                            </select>
                          </div>
                        );
                      })}
                    </div>

                    {activeBatch.sampleRows.length > 0 ? (
                      <div className="overflow-x-auto rounded-lg border border-border-subtle">
                        <table className="w-full text-left text-xs">
                          <thead className="bg-slate-50 text-text-secondary">
                            <tr>
                              {activeBatch.sourceHeaders.map((header) => (
                                <th key={header} className="px-3 py-2 font-semibold">
                                  {header}
                                </th>
                              ))}
                            </tr>
                          </thead>
                          <tbody className="divide-y divide-border-subtle bg-white">
                            {activeBatch.sampleRows.map((row, idx) => (
                              <tr key={idx}>
                                {activeBatch.sourceHeaders.map((header) => (
                                  <td key={header} className="px-3 py-1.5 text-text-main">
                                    {row[header] || "—"}
                                  </td>
                                ))}
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    ) : null}

                    {canIssue && !isLockedForEditing ? (
                      <div className="flex flex-wrap items-center justify-end gap-2">
                        <Button
                          type="button"
                          variant="outline"
                          disabled={busyAction !== null}
                          onClick={handleSaveMapping}
                        >
                          {busyAction === "mapping" ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : null}
                          Save Mapping
                        </Button>
                        <Button
                          type="button"
                          variant="primary"
                          disabled={busyAction !== null}
                          onClick={handleValidate}
                        >
                          {busyAction === "validate" ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : (
                            <CheckCircle2 className="h-4 w-4" />
                          )}
                          Step 3 · Validate Rows
                        </Button>
                      </div>
                    ) : null}
                  </CardContent>
                </Card>

                <Card>
                  <CardHeader>
                    <CardTitle className="text-base">
                      Step 4 · Validation, Execution & ZIP
                    </CardTitle>
                    <CardDescription>
                      Asynchronous worker renders PDF & PNG certificates idempotently per row.
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                      <div className="rounded-lg border border-border-subtle bg-slate-50/70 p-3">
                        <div className="text-xs text-text-secondary">Total Rows</div>
                        <div className="mt-1 text-lg font-semibold text-text-main">
                          {activeBatch.totalRecords}
                        </div>
                      </div>
                      <div className="rounded-lg border border-emerald-200 bg-emerald-50/60 p-3">
                        <div className="text-xs text-emerald-800">Valid / Ready</div>
                        <div className="mt-1 text-lg font-semibold text-emerald-950">
                          {activeBatch.validRecords}
                        </div>
                      </div>
                      <div className="rounded-lg border border-amber-200 bg-amber-50/60 p-3">
                        <div className="text-xs text-amber-800">Invalid Rows</div>
                        <div className="mt-1 text-lg font-semibold text-amber-950">
                          {activeBatch.invalidRecords}
                        </div>
                      </div>
                      <div className="rounded-lg border border-border-subtle bg-white p-3">
                        <div className="text-xs text-text-secondary">Issued / Failed</div>
                        <div className="mt-1 text-lg font-semibold text-text-main">
                          {activeBatch.successfulRecords} / {activeBatch.failedRecords}
                        </div>
                      </div>
                    </div>

                    {activeBatch.status === "QUEUED" || activeBatch.status === "PROCESSING" ? (
                      <div className="space-y-2 rounded-lg border border-blue-200 bg-blue-50/50 p-3">
                        <div className="flex items-center justify-between text-xs font-medium text-brand-navy">
                          <span className="inline-flex items-center gap-1.5">
                            <Loader2 className="h-3.5 w-3.5 animate-spin" />
                            Generating certificates asynchronously...
                          </span>
                          <span>{progressPercent}%</span>
                        </div>
                        <div className="h-2 overflow-hidden rounded-full bg-blue-100">
                          <div
                            className="h-full bg-brand-blue transition-all"
                            style={{ width: `${progressPercent}%` }}
                          />
                        </div>
                      </div>
                    ) : null}

                    {activeBatch.errorSummary ? (
                      <p className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
                        {activeBatch.errorSummary}
                      </p>
                    ) : null}

                    <div className="flex flex-wrap items-center gap-2">
                      {canIssue &&
                      (activeBatch.status === "VALIDATED" ||
                        activeBatch.status === "MAPPED" ||
                        activeBatch.status === "UPLOADED") ? (
                        <Button
                          type="button"
                          variant="primary"
                          disabled={busyAction !== null}
                          onClick={handleGenerate}
                        >
                          {busyAction === "generate" ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : (
                            <Play className="h-4 w-4" />
                          )}
                          Start Bulk Generation
                        </Button>
                      ) : null}

                      {canIssue && activeBatch.failedRecords > 0 ? (
                        <Button
                          type="button"
                          variant="outline"
                          disabled={busyAction !== null}
                          onClick={handleRetryFailed}
                        >
                          <RefreshCw className="h-4 w-4" />
                          Retry Failed Rows
                        </Button>
                      ) : null}

                      {activeBatch.zipAvailable ? (
                        <Button
                          type="button"
                          variant="primary"
                          disabled={busyAction !== null}
                          onClick={() => handleDownloadZip(activeBatch.id)}
                        >
                          {busyAction === "download" ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : (
                            <Download className="h-4 w-4" />
                          )}
                          Download ZIP Archive
                        </Button>
                      ) : null}
                    </div>

                    {batchErrors.length > 0 ? (
                      <div className="space-y-2 pt-2">
                        <div className="flex items-center gap-1.5 text-xs font-semibold text-amber-800 uppercase">
                          <AlertTriangle className="h-3.5 w-3.5" />
                          Row-Level Validation & Processing Errors ({batchErrors.length})
                        </div>
                        <div className="max-h-60 overflow-auto rounded-lg border border-border-subtle">
                          <table className="w-full text-left text-xs">
                            <thead className="bg-slate-50 text-text-secondary">
                              <tr>
                                <th className="px-3 py-2">Row #</th>
                                <th className="px-3 py-2">Status</th>
                                <th className="px-3 py-2">Error Details</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-border-subtle bg-white">
                              {batchErrors.map((errItem) => (
                                <tr key={errItem.id}>
                                  <td className="px-3 py-2 font-mono font-medium">
                                    #{errItem.rowNumber}
                                  </td>
                                  <td className="px-3 py-2">
                                    <Badge variant="danger">{errItem.status}</Badge>
                                  </td>
                                  <td className="px-3 py-2 text-rose-700">
                                    {errItem.errorMessage ?? "Invalid row"}
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </div>
                    ) : null}
                  </CardContent>
                </Card>
              </>
            ) : (
              <StatePanel
                variant="empty"
                title="No batch selected"
                description="Upload a new CSV/XLSX roster above or choose a recent batch from the right panel."
              />
            )}
          </div>

          <Card className="h-fit">
            <CardHeader>
              <div className="flex items-center justify-between gap-2">
                <div>
                  <CardTitle className="flex items-center gap-2 text-base">
                    <Archive className="h-4 w-4 text-brand-navy" />
                    Recent Batch Jobs
                  </CardTitle>
                  <CardDescription>
                    Resume validation, check progress, or download ZIPs.
                  </CardDescription>
                </div>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => setRefreshTick((value) => value + 1)}
                  title="Refresh list"
                >
                  <RefreshCw className="h-3.5 w-3.5" />
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {recentBatches.length === 0 ? (
                <p className="text-sm text-text-secondary">
                  No bulk certificate batches found yet.
                </p>
              ) : (
                <div className="space-y-2.5">
                  {recentBatches.map((batch) => {
                    const isSelected = activeBatch?.id === batch.id;
                    return (
                      <button
                        key={batch.id}
                        type="button"
                        onClick={() => void selectExistingBatch(batch.id)}
                        className={`w-full rounded-lg border p-3 text-left transition-colors ${
                          isSelected
                            ? "border-brand-navy bg-brand-navy/5"
                            : "border-border-subtle bg-white hover:bg-slate-50"
                        }`}
                      >
                        <div className="flex items-center justify-between gap-2">
                          <span className="truncate text-sm font-medium text-text-main">
                            {batch.sourceFileName}
                          </span>
                          <BatchStatusBadge status={batch.status} />
                        </div>
                        <div className="mt-1 text-xs text-text-secondary">
                          {templateMap.get(batch.templateId) ?? "Template"}
                        </div>
                        <div className="mt-2 flex items-center justify-between text-[11px] text-text-secondary">
                          <span>
                            {batch.successfulRecords}/{batch.totalRecords} issued
                          </span>
                          <span>{formatDateTime(batch.createdAt)}</span>
                        </div>
                      </button>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}

"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  Archive,
  CheckCircle2,
  Copy,
  ExternalLink,
  Eye,
  FilePlus2,
  Loader2,
  RefreshCw,
  Search,
  X,
} from "lucide-react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useAuth } from "@/components/auth/auth-provider";
import { RequireAuthPanel } from "@/components/auth/require-auth-panel";
import { CertificatePreviewCanvas } from "@/components/templates/certificate-preview-canvas";
import { TemplateStatusBadge } from "@/components/templates/template-status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { StatePanel } from "@/components/ui/state-panel";
import {
  archiveTemplate,
  createTemplate,
  duplicateTemplate,
  listTemplates,
  previewTemplate,
  publishTemplate,
} from "@/lib/api/templates";
import {
  ApiClientError,
  type PageMeta,
  type TemplateOrientation,
  type TemplatePreview,
  type TemplateStatus,
  type TemplateSummary,
} from "@/lib/api/types";
import { CERTIFICATE_STUDIO_PRESETS } from "@/lib/editor/design-presets";

const createTemplateSchema = z.object({
  name: z.string().trim().min(1, "Template name is required.").max(180),
  description: z.string().trim().max(1000).optional(),
  category: z.string().trim().max(80).optional(),
  orientation: z.enum(["LANDSCAPE", "PORTRAIT"]),
});

type CreateTemplateValues = z.infer<typeof createTemplateSchema>;

const SAMPLE_PREVIEW_SETS = {
  en: {
    label: "English Sample",
    values: {
      recipientName: "Alex Rivera",
      courseName: "Full-Stack Cloud Engineering",
      issuedDate: "November 12, 2026",
      certificateNumber: "CERT-2026-000184",
    },
  },
  km: {
    label: "Khmer + Latin Sample",
    values: {
      recipientName: "សុខ ដារ៉ា (Sok Dara)",
      courseName: "វិញ្ញាបនបត្របញ្ចប់វគ្គសិក្សា",
      issuedDate: "១២ វិច្ឆិកា ២០២៦",
      certificateNumber: "CERT-2026-000185",
    },
  },
} as const;

interface TemplateGalleryViewProps {
  mode: "gallery" | "drafts";
  title: string;
  description: string;
}

function TemplateGalleryContent({ mode, title, description }: TemplateGalleryViewProps) {
  const router = useRouter();
  const { hasPermission } = useAuth();
  const canRead = hasPermission("template:read");
  const canWrite = hasPermission("template:write");
  const canPublish = hasPermission("template:publish");

  const [templates, setTemplates] = React.useState<TemplateSummary[]>([]);
  const [pagination, setPagination] = React.useState<PageMeta>({
    page: 0,
    size: 12,
    totalElements: 0,
    totalPages: 0,
  });
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [feedback, setFeedback] = React.useState<{
    type: "success" | "error";
    text: string;
    fieldErrors?: Record<string, string>;
  } | null>(null);

  const [searchInput, setSearchInput] = React.useState("");
  const [appliedQuery, setAppliedQuery] = React.useState("");
  const [statusFilter, setStatusFilter] = React.useState<TemplateStatus | "">(
    mode === "drafts" ? "DRAFT" : ""
  );
  const [orientationFilter, setOrientationFilter] = React.useState<TemplateOrientation | "">("");
  const [page, setPage] = React.useState(0);
  const [reloadTick, setReloadTick] = React.useState(0);

  const [showCreateForm, setShowCreateForm] = React.useState(false);
  const [selectedPresetId, setSelectedPresetId] = React.useState<string>("navy-gold-ribbon");
  const [busyActionId, setBusyActionId] = React.useState<string | null>(null);

  const [previewState, setPreviewState] = React.useState<{
    templateName: string;
    sampleMode: "en" | "km";
    loading: boolean;
    data: TemplatePreview | null;
    error: string | null;
    templateId: string;
  } | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CreateTemplateValues>({
    resolver: zodResolver(createTemplateSchema),
    defaultValues: {
      name: "",
      description: "",
      category: "Completion",
      orientation: "LANDSCAPE",
    },
  });

  React.useEffect(() => {
    if (!canRead) {
      return;
    }
    const controller = new AbortController();
    let active = true;
    const effectiveStatus = mode === "drafts" ? "DRAFT" : statusFilter;

    listTemplates(
      {
        q: appliedQuery,
        status: effectiveStatus,
        orientation: orientationFilter,
        page,
        size: 12,
      },
      controller.signal
    )
      .then((res) => {
        if (!active) return;
        setTemplates(res.data);
        if (res.meta?.pagination) {
          setPagination(res.meta.pagination);
        }
        setError(null);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (!active || (err instanceof DOMException && err.name === "AbortError")) {
          return;
        }
        setError(err instanceof ApiClientError ? err.message : "Failed to load templates.");
        setLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [canRead, mode, appliedQuery, statusFilter, orientationFilter, page, reloadTick]);

  const triggerReload = (resetPage = false) => {
    setLoading(true);
    if (resetPage) setPage(0);
    setReloadTick((t) => t + 1);
  };

  if (!canRead) {
    return (
      <StatePanel
        variant="error"
        title="Template access restricted"
        description="Your account role does not include template:read permission."
      />
    );
  }

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setPage(0);
    setAppliedQuery(searchInput.trim());
  };

  const onCreateSubmit = async (values: CreateTemplateValues) => {
    setFeedback(null);
    try {
      const selectedPreset = CERTIFICATE_STUDIO_PRESETS.find(
        (preset) => preset.id === selectedPresetId
      );
      const designDoc = selectedPreset ? selectedPreset.build(values.orientation) : undefined;
      const response = await createTemplate({
        name: values.name,
        description: values.description || undefined,
        category: values.category || selectedPreset?.category || undefined,
        orientation: values.orientation,
        pageWidth: designDoc?.page.width,
        pageHeight: designDoc?.page.height,
        designJson: designDoc,
      });
      reset();
      setShowCreateForm(false);
      router.push(`/studio/templates/${response.data.id}`);
    } catch (err) {
      setFeedback({
        type: "error",
        text: err instanceof ApiClientError ? err.message : "Failed to create draft template.",
        fieldErrors: err instanceof ApiClientError ? err.fieldErrors : undefined,
      });
    }
  };

  const runPreview = async (templateId: string, templateName: string, sampleMode: "en" | "km") => {
    setPreviewState({
      templateId,
      templateName,
      sampleMode,
      loading: true,
      data: null,
      error: null,
    });
    try {
      const res = await previewTemplate(templateId, {
        sampleData: SAMPLE_PREVIEW_SETS[sampleMode].values,
      });
      setPreviewState({
        templateId,
        templateName,
        sampleMode,
        loading: false,
        data: res.data,
        error: null,
      });
    } catch (err) {
      setPreviewState({
        templateId,
        templateName,
        sampleMode,
        loading: false,
        data: null,
        error: err instanceof ApiClientError ? err.message : "Failed to render template preview.",
      });
    }
  };

  const handleDuplicate = async (item: TemplateSummary) => {
    setBusyActionId(item.id);
    setFeedback(null);
    try {
      const res = await duplicateTemplate(item.id);
      setFeedback({
        type: "success",
        text: `Created draft duplicate "${res.data.name}".`,
      });
      triggerReload(true);
    } catch (err) {
      setFeedback({
        type: "error",
        text: err instanceof ApiClientError ? err.message : "Failed to duplicate template.",
      });
    } finally {
      setBusyActionId(null);
    }
  };

  const handlePublish = async (item: TemplateSummary) => {
    setBusyActionId(item.id);
    setFeedback(null);
    try {
      const res = await publishTemplate(item.id);
      setFeedback({
        type: "success",
        text: `Published "${res.data.name}" at immutable version v${res.data.publishedVersion}.`,
      });
      triggerReload(false);
    } catch (err) {
      setFeedback({
        type: "error",
        text: err instanceof ApiClientError ? err.message : "Failed to publish template.",
      });
    } finally {
      setBusyActionId(null);
    }
  };

  const handleArchive = async (item: TemplateSummary) => {
    setBusyActionId(item.id);
    setFeedback(null);
    try {
      const res = await archiveTemplate(item.id);
      setFeedback({
        type: "success",
        text: `Archived "${res.data.name}". Historical versions remain preserved.`,
      });
      triggerReload(false);
    } catch (err) {
      setFeedback({
        type: "error",
        text: err instanceof ApiClientError ? err.message : "Failed to archive template.",
      });
    } finally {
      setBusyActionId(null);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-wrap items-center gap-3">
          {canWrite ? (
            <Button
              size="sm"
              onClick={() => setShowCreateForm((v) => !v)}
              className="h-9 bg-[#1E6B52] px-4 text-white shadow-xs hover:bg-[#175541]"
            >
              <FilePlus2 className="h-4 w-4" />
              {showCreateForm ? "Close Designer" : "Create New Design"}
            </Button>
          ) : null}
          <div>
            <h1 className="text-xl font-bold tracking-tight text-text-main">{title}</h1>
            <p className="text-xs text-text-secondary">{description}</p>
          </div>
        </div>
        <Button variant="outline" size="sm" onClick={() => triggerReload(false)}>
          <RefreshCw className="h-4 w-4" />
          Refresh
        </Button>
      </div>

      {feedback ? (
        <div
          className={`rounded-lg border px-4 py-3 text-sm ${
            feedback.type === "success"
              ? "border-emerald-200 bg-emerald-50 text-emerald-800"
              : "border-rose-200 bg-rose-50 text-rose-700"
          }`}
        >
          <p>{feedback.text}</p>
          {feedback.fieldErrors && Object.keys(feedback.fieldErrors).length > 0 ? (
            <ul className="mt-1 list-disc pl-5 text-xs">
              {Object.entries(feedback.fieldErrors).map(([field, msg]) => (
                <li key={field}>
                  <code className="font-mono">{field}</code>: {msg}
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}

      {showCreateForm && canWrite ? (
        <Card className="border-[#1E6B52]/30 shadow-sm">
          <CardHeader>
            <CardTitle>Create New Certificate Design</CardTitle>
            <CardDescription>
              Pick a curated visual starter style or start blank, then customize typography, QR,
              signatures, and assets in the Fabric.js Studio.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <div>
              <p className="mb-2 text-xs font-semibold text-text-main">Choose Starter Layout</p>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
                {CERTIFICATE_STUDIO_PRESETS.map((preset) => {
                  const active = selectedPresetId === preset.id;
                  return (
                    <button
                      key={preset.id}
                      type="button"
                      onClick={() => setSelectedPresetId(preset.id)}
                      className={`overflow-hidden rounded-xl border text-left transition-all ${
                        active
                          ? "border-[#1E6B52] ring-2 ring-[#1E6B52]/25"
                          : "border-border-subtle hover:border-slate-300"
                      }`}
                    >
                      <div className="border-b border-border-subtle bg-slate-100 p-2">
                        <CertificatePreviewCanvas
                          design={preset.build("LANDSCAPE")}
                          resolvedPlaceholders={{
                            recipientName: "Sok Dara",
                            courseName: "Leadership",
                            organizationName: "SmartCert",
                            issuedDate: "Nov 12",
                            certificateNumber: "SC-001",
                            issuerName: "Director",
                          }}
                          className="mx-auto max-h-24"
                        />
                      </div>
                      <div className="p-2">
                        <p className="truncate text-xs font-semibold text-text-main">
                          {preset.name}
                        </p>
                        <p className="text-[10px] text-text-secondary">{preset.styleTag}</p>
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>

            <form onSubmit={handleSubmit(onCreateSubmit)} className="grid gap-4 md:grid-cols-2">
              <div>
                <label className="mb-1 block text-xs font-medium text-text-main">Design Name</label>
                <Input placeholder="e.g. Executive Leadership Certificate" {...register("name")} />
                {errors.name ? (
                  <p className="mt-1 text-xs text-rose-600">{errors.name.message}</p>
                ) : null}
              </div>

              <div>
                <label className="mb-1 block text-xs font-medium text-text-main">Category</label>
                <Input placeholder="e.g. Completion, Academic, Award" {...register("category")} />
              </div>

              <div>
                <label className="mb-1 block text-xs font-medium text-text-main">
                  Page Orientation Preset
                </label>
                <select
                  {...register("orientation")}
                  className="flex h-9 w-full rounded-lg border border-border-subtle bg-surface px-3 py-1.5 text-sm text-text-main focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue"
                >
                  <option value="LANDSCAPE">Landscape — A4 (842 × 595 pt)</option>
                  <option value="PORTRAIT">Portrait — A4 (595 × 842 pt)</option>
                </select>
              </div>

              <div>
                <label className="mb-1 block text-xs font-medium text-text-main">
                  Description (optional)
                </label>
                <Input
                  placeholder="Short internal description for issuers"
                  {...register("description")}
                />
              </div>

              <div className="flex items-center justify-end gap-2 md:col-span-2">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => setShowCreateForm(false)}
                >
                  Cancel
                </Button>
                <Button
                  type="submit"
                  size="sm"
                  disabled={isSubmitting}
                  className="bg-[#1E6B52] text-white hover:bg-[#175541]"
                >
                  {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                  Create &amp; Open Studio
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      ) : null}

      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border-subtle bg-surface px-4 py-3 shadow-2xs">
        {mode === "gallery" ? (
          <div className="flex flex-wrap items-center gap-1 rounded-lg bg-slate-100 p-1">
            {(
              [
                { value: "", label: "All Designs" },
                { value: "PUBLISHED", label: "Published" },
                { value: "DRAFT", label: "Drafts" },
                { value: "ARCHIVED", label: "Archived" },
              ] as const
            ).map((tab) => {
              const active = statusFilter === tab.value;
              return (
                <button
                  key={tab.value || "all"}
                  type="button"
                  onClick={() => {
                    setLoading(true);
                    setPage(0);
                    setStatusFilter(tab.value);
                  }}
                  className={`rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
                    active
                      ? "bg-[#1E6B52]/15 text-[#1E6B52] shadow-2xs"
                      : "text-text-secondary hover:text-text-main"
                  }`}
                >
                  {tab.label}
                </button>
              );
            })}
          </div>
        ) : (
          <Badge variant="gold">Draft Iterations</Badge>
        )}

        <form onSubmit={handleSearchSubmit} className="flex flex-wrap items-center gap-2">
          <div className="relative min-w-56">
            <Search className="pointer-events-none absolute top-2.5 left-3 h-4 w-4 text-text-secondary" />
            <Input
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              placeholder="Search designs..."
              className="pl-9"
            />
          </div>

          <select
            aria-label="Filter by orientation"
            value={orientationFilter}
            onChange={(e) => {
              setLoading(true);
              setPage(0);
              setOrientationFilter(e.target.value as TemplateOrientation | "");
            }}
            className="h-9 rounded-lg border border-border-subtle bg-surface px-3 text-sm text-text-main focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue"
          >
            <option value="">All Orientations</option>
            <option value="LANDSCAPE">Landscape</option>
            <option value="PORTRAIT">Portrait</option>
          </select>

          <Button type="submit" variant="outline" size="sm">
            Search
          </Button>
        </form>
      </div>

      {loading ? (
        <StatePanel
          variant="loading"
          title="Loading templates"
          description="Fetching your organization certificate templates and preview assets."
        />
      ) : error ? (
        <StatePanel
          variant="error"
          title="Unable to load templates"
          description={error}
          actionLabel="Retry"
          onAction={() => triggerReload(false)}
        />
      ) : templates.length === 0 ? (
        <StatePanel
          variant="empty"
          title={mode === "drafts" ? "No draft designs found" : "No templates match your filters"}
          description={
            mode === "drafts"
              ? "Create a new draft template or duplicate an existing design to start iterating."
              : "Create your first organization certificate template or clear active search filters."
          }
          actionLabel={canWrite ? "Create Draft Template" : undefined}
          onAction={canWrite ? () => setShowCreateForm(true) : undefined}
        />
      ) : (
        <>
          <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
            {templates.map((item) => {
              const busy = busyActionId === item.id;
              return (
                <Card key={item.id} className="flex flex-col justify-between overflow-hidden">
                  <div className="border-b border-border-subtle bg-slate-50/70 p-4">
                    <CertificatePreviewCanvas
                      design={item.previewDesign}
                      assetUrls={item.assetUrls}
                      className="mx-auto max-h-52"
                    />
                  </div>

                  <CardHeader className="pb-3">
                    <div className="flex items-start justify-between gap-2">
                      <CardTitle className="line-clamp-1 text-base">{item.name}</CardTitle>
                      <TemplateStatusBadge status={item.status} />
                    </div>
                    <CardDescription className="line-clamp-2 min-h-9">
                      {item.description || "No internal description provided."}
                    </CardDescription>
                    <div className="mt-2 flex flex-wrap items-center gap-1.5 text-xs text-text-secondary">
                      <Badge variant="navy">
                        {item.orientation === "LANDSCAPE" ? "Landscape" : "Portrait"}
                      </Badge>
                      <Badge variant="neutral">v{item.currentVersion}</Badge>
                      {item.publishedVersion ? (
                        <Badge variant="gold">Live v{item.publishedVersion}</Badge>
                      ) : null}
                      {item.category ? <Badge variant="blue">{item.category}</Badge> : null}
                    </div>
                  </CardHeader>

                  <CardContent className="pt-0">
                    <div className="mb-3 flex items-center justify-between text-xs text-text-secondary">
                      <span>
                        {item.pageWidth} × {item.pageHeight} pt
                      </span>
                      <span>Updated {new Date(item.updatedAt).toLocaleDateString()}</span>
                    </div>

                    <div className="flex flex-wrap items-center gap-2 border-t border-border-subtle pt-3">
                      <Link
                        href={`/studio/templates/${item.id}`}
                        className="inline-flex h-8 flex-1 items-center justify-center gap-1.5 rounded-lg bg-[#1E6B52] px-3 text-xs font-medium text-white transition-colors hover:bg-[#175541]"
                      >
                        <ExternalLink className="h-3.5 w-3.5" />
                        Edit Design
                      </Link>

                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        title="Preview PDF/HTML"
                        className="h-8 px-2.5 text-xs"
                        onClick={() => runPreview(item.id, item.name, "en")}
                      >
                        <Eye className="h-3.5 w-3.5" />
                      </Button>

                      {canWrite ? (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          title="Duplicate"
                          className="h-8 px-2.5 text-xs"
                          disabled={busy}
                          onClick={() => handleDuplicate(item)}
                        >
                          <Copy className="h-3.5 w-3.5" />
                        </Button>
                      ) : null}

                      {canPublish && item.status === "DRAFT" ? (
                        <Button
                          type="button"
                          variant="accent"
                          size="sm"
                          className="h-8 px-2.5 text-xs"
                          disabled={busy}
                          onClick={() => handlePublish(item)}
                        >
                          <CheckCircle2 className="h-3.5 w-3.5" />
                          Publish
                        </Button>
                      ) : null}

                      {canPublish && item.status !== "ARCHIVED" ? (
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          title="Archive"
                          className="h-8 px-2 text-xs text-text-secondary hover:text-rose-700"
                          disabled={busy}
                          onClick={() => handleArchive(item)}
                        >
                          <Archive className="h-3.5 w-3.5" />
                        </Button>
                      ) : null}
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>

          {pagination.totalPages > 1 ? (
            <div className="flex items-center justify-between border-t border-border-subtle pt-4 text-sm text-text-secondary">
              <span>
                Page {pagination.page + 1} of {pagination.totalPages} ({pagination.totalElements}{" "}
                templates)
              </span>
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={pagination.page <= 0}
                  onClick={() => {
                    setLoading(true);
                    setPage((p) => Math.max(0, p - 1));
                  }}
                >
                  Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={pagination.page + 1 >= pagination.totalPages}
                  onClick={() => {
                    setLoading(true);
                    setPage((p) => p + 1);
                  }}
                >
                  Next
                </Button>
              </div>
            </div>
          ) : null}
        </>
      )}

      {previewState ? (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4"
        >
          <div className="max-h-[92vh] w-full max-w-4xl overflow-y-auto rounded-xl border border-border-subtle bg-surface p-6 shadow-xl">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-border-subtle pb-3">
              <div>
                <h2 className="text-lg font-semibold text-text-main">
                  Preview: {previewState.templateName}
                </h2>
                <p className="text-xs text-text-secondary">
                  Server-resolved sample placeholder preview (version{" "}
                  {previewState.data?.versionNumber ?? "-"})
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  size="sm"
                  variant={previewState.sampleMode === "en" ? "primary" : "outline"}
                  onClick={() =>
                    runPreview(previewState.templateId, previewState.templateName, "en")
                  }
                >
                  English
                </Button>
                <Button
                  size="sm"
                  variant={previewState.sampleMode === "km" ? "primary" : "outline"}
                  onClick={() =>
                    runPreview(previewState.templateId, previewState.templateName, "km")
                  }
                >
                  Khmer
                </Button>
                <Button
                  size="sm"
                  variant="ghost"
                  aria-label="Close preview"
                  onClick={() => setPreviewState(null)}
                >
                  <X className="h-4 w-4" />
                </Button>
              </div>
            </div>

            {previewState.loading ? (
              <StatePanel
                variant="loading"
                title="Generating server preview"
                description="Validating design JSON and resolving signed organization assets."
              />
            ) : previewState.error ? (
              <StatePanel variant="error" title="Preview failed" description={previewState.error} />
            ) : previewState.data ? (
              <div className="space-y-4">
                {previewState.data.warnings.length > 0 ? (
                  <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800">
                    <p className="font-semibold">Design Warnings</p>
                    <ul className="mt-1 list-disc pl-4">
                      {previewState.data.warnings.map((w) => (
                        <li key={w}>{w}</li>
                      ))}
                    </ul>
                  </div>
                ) : null}

                <div className="rounded-lg bg-slate-100 p-4">
                  <CertificatePreviewCanvas
                    design={previewState.data.resolvedDesignJson}
                    assetUrls={previewState.data.assetUrls}
                    resolvedPlaceholders={previewState.data.resolvedPlaceholders}
                    className="mx-auto max-w-2xl"
                  />
                </div>
              </div>
            ) : null}
          </div>
        </div>
      ) : null}
    </div>
  );
}

export function TemplateGalleryView(props: TemplateGalleryViewProps) {
  return (
    <RequireAuthPanel>
      <TemplateGalleryContent {...props} />
    </RequireAuthPanel>
  );
}

"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  CheckCircle2,
  Download,
  ExternalLink,
  Image as ImageIcon,
  Loader2,
  Plus,
  RefreshCw,
  Sparkles,
  Trash2,
} from "lucide-react";
import { useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { useAuth } from "@/components/auth/auth-provider";
import { RequireAuthPanel } from "@/components/auth/require-auth-panel";
import { CertificateStatusBadge } from "@/components/certificates/certificate-status-badge";
import { CertificatePreviewCanvas } from "@/components/templates/certificate-preview-canvas";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { StatePanel } from "@/components/ui/state-panel";
import {
  downloadCertificateBlob,
  issueCertificate,
  triggerBrowserBlobDownload,
} from "@/lib/api/certificates";
import { getTemplateVersion, listTemplates } from "@/lib/api/templates";
import {
  ApiClientError,
  type CertificateDetail,
  type CertificateFormat,
  type DesignDocument,
  type TemplateSummary,
} from "@/lib/api/types";

const issueCertificateSchema = z.object({
  templateId: z.string().min(1, "Select a published template."),
  recipientName: z.string().trim().min(1, "Recipient name is required.").max(160),
  recipientEmail: z
    .string()
    .trim()
    .max(255)
    .refine(
      (value) => value === "" || z.string().email().safeParse(value).success,
      "Enter a valid email address."
    ),
  courseName: z.string().trim().min(1, "Course or program name is required.").max(200),
  completionDate: z.string().optional(),
  expiresAtDate: z.string().optional(),
});

type IssueCertificateValues = z.infer<typeof issueCertificateSchema>;

interface MetadataRow {
  id: string;
  key: string;
  value: string;
}

function createIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `sc-issue-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export default function NewCertificatePage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const requestedTemplateId = searchParams.get("templateId") ?? "";
  const { status: authStatus, session, hasPermission } = useAuth();
  const canIssue = hasPermission("certificate:issue");

  const [templates, setTemplates] = React.useState<TemplateSummary[]>([]);
  const [templatesLoading, setTemplatesLoading] = React.useState(false);
  const [templatesError, setTemplatesError] = React.useState<string | null>(null);
  const [reloadTick, setReloadTick] = React.useState(0);

  const [publishedDesignState, setPublishedDesignState] = React.useState<{
    templateId: string;
    version: number;
    design: DesignDocument;
  } | null>(null);
  const [designLoading, setDesignLoading] = React.useState(false);

  const [metadataRows, setMetadataRows] = React.useState<MetadataRow[]>([]);
  const [idempotencyKey, setIdempotencyKey] = React.useState<string>(() => createIdempotencyKey());
  const [submitError, setSubmitError] = React.useState<string | null>(null);
  const [submitting, setSubmitting] = React.useState(false);
  const [issuedCertificate, setIssuedCertificate] = React.useState<CertificateDetail | null>(null);
  const [downloadingFormat, setDownloadingFormat] = React.useState<CertificateFormat | null>(null);

  const {
    register,
    control,
    handleSubmit,
    setValue,
    reset,
    formState: { errors },
  } = useForm<IssueCertificateValues>({
    resolver: zodResolver(issueCertificateSchema),
    defaultValues: {
      templateId: requestedTemplateId,
      recipientName: "",
      recipientEmail: "",
      courseName: "",
      completionDate: new Date().toISOString().slice(0, 10),
      expiresAtDate: "",
    },
  });

  const watchedValues = useWatch({ control });
  const selectedTemplateId = watchedValues.templateId ?? "";

  React.useEffect(() => {
    if (authStatus !== "authenticated" || !canIssue) return;

    const controller = new AbortController();
    let active = true;

    async function loadPublishedTemplates() {
      setTemplatesLoading(true);
      setTemplatesError(null);
      try {
        const response = await listTemplates({ size: 100 }, controller.signal);
        if (!active) return;
        const eligible = response.data.filter(
          (tpl) => tpl.publishedVersion !== null && tpl.status !== "ARCHIVED"
        );
        setTemplates(eligible);

        if (eligible.length > 0) {
          const preferred = eligible.find((tpl) => tpl.id === requestedTemplateId) ?? eligible[0];
          setValue("templateId", preferred.id, { shouldValidate: false });
        }
      } catch (err) {
        if (!active || controller.signal.aborted) return;
        setTemplatesError(
          err instanceof ApiClientError ? err.message : "Failed to load published templates."
        );
      } finally {
        if (active) {
          setTemplatesLoading(false);
        }
      }
    }

    void loadPublishedTemplates();
    return () => {
      active = false;
      controller.abort();
    };
  }, [authStatus, canIssue, reloadTick, requestedTemplateId, setValue]);

  const selectedTemplate = React.useMemo(
    () => templates.find((tpl) => tpl.id === selectedTemplateId) ?? null,
    [templates, selectedTemplateId]
  );

  React.useEffect(() => {
    if (!selectedTemplate || selectedTemplate.publishedVersion === null) {
      return;
    }

    const controller = new AbortController();
    let active = true;
    const templateId = selectedTemplate.id;
    const versionNumber = selectedTemplate.publishedVersion;

    async function loadVersion() {
      setDesignLoading(true);
      try {
        const response = await getTemplateVersion(templateId, versionNumber, controller.signal);
        if (!active) return;
        setPublishedDesignState({
          templateId,
          version: versionNumber,
          design: response.data.designJson,
        });
      } catch {
        if (!active || controller.signal.aborted) return;
        if (selectedTemplate?.previewDesign) {
          setPublishedDesignState({
            templateId,
            version: versionNumber,
            design: selectedTemplate.previewDesign,
          });
        }
      } finally {
        if (active) {
          setDesignLoading(false);
        }
      }
    }

    void loadVersion();
    return () => {
      active = false;
      controller.abort();
    };
  }, [selectedTemplate]);

  const effectiveDesign = React.useMemo(() => {
    if (!selectedTemplate) return null;
    if (
      publishedDesignState &&
      publishedDesignState.templateId === selectedTemplate.id &&
      publishedDesignState.version === selectedTemplate.publishedVersion
    ) {
      return publishedDesignState.design;
    }
    return selectedTemplate.previewDesign;
  }, [publishedDesignState, selectedTemplate]);

  const previewPlaceholders = React.useMemo(() => {
    const placeholders: Record<string, string> = {};
    for (const row of metadataRows) {
      const k = row.key.trim();
      if (k) {
        placeholders[k] = row.value;
      }
    }
    placeholders.recipientName = watchedValues.recipientName?.trim() || "Recipient Full Name";
    placeholders.recipientEmail = watchedValues.recipientEmail?.trim() || "recipient@example.org";
    placeholders.courseName = watchedValues.courseName?.trim() || "Program or Course Title";
    placeholders.completionDate =
      watchedValues.completionDate || new Date().toISOString().slice(0, 10);
    placeholders.issuedDate = new Date().toISOString().slice(0, 10);
    placeholders.certificateNumber = issuedCertificate?.certificateNumber ?? "SC-PREVIEW-000001";
    placeholders.organizationName = session?.organization.name ?? "Issuing Organization";
    return placeholders;
  }, [
    issuedCertificate?.certificateNumber,
    metadataRows,
    session?.organization.name,
    watchedValues.completionDate,
    watchedValues.courseName,
    watchedValues.recipientEmail,
    watchedValues.recipientName,
  ]);

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

  if (!canIssue) {
    return (
      <StatePanel
        variant="error"
        title="Issuance Restricted"
        description="Your role does not include certificate:issue permission."
      />
    );
  }

  const onSubmit = async (values: IssueCertificateValues) => {
    setSubmitting(true);
    setSubmitError(null);
    try {
      const metadata: Record<string, string> = {};
      for (const row of metadataRows) {
        const cleanKey = row.key.trim();
        if (cleanKey) {
          metadata[cleanKey] = row.value.trim();
        }
      }

      const response = await issueCertificate({
        templateId: values.templateId,
        recipientName: values.recipientName.trim(),
        recipientEmail: values.recipientEmail.trim() || undefined,
        courseName: values.courseName.trim(),
        completionDate: values.completionDate || undefined,
        expiresAt: values.expiresAtDate
          ? new Date(`${values.expiresAtDate}T23:59:59Z`).toISOString()
          : undefined,
        metadata: Object.keys(metadata).length > 0 ? metadata : undefined,
        idempotencyKey,
      });

      setIssuedCertificate(response.data);
    } catch (err) {
      setSubmitError(err instanceof ApiClientError ? err.message : "Failed to issue certificate.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleDownloadIssued = async (format: CertificateFormat) => {
    if (!issuedCertificate) return;
    setDownloadingFormat(format);
    setSubmitError(null);
    try {
      const blob = await downloadCertificateBlob(issuedCertificate.id, format, false);
      const ext = format === "PDF" ? "pdf" : "png";
      triggerBrowserBlobDownload(blob, `${issuedCertificate.certificateNumber}.${ext}`);
    } catch (err) {
      setSubmitError(err instanceof ApiClientError ? err.message : `Failed to download ${format}.`);
    } finally {
      setDownloadingFormat(null);
    }
  };

  const handleResetForAnother = () => {
    setIssuedCertificate(null);
    setSubmitError(null);
    setIdempotencyKey(createIdempotencyKey());
    reset({
      templateId: selectedTemplate?.id ?? templates[0]?.id ?? "",
      recipientName: "",
      recipientEmail: "",
      courseName: watchedValues.courseName ?? "",
      completionDate: watchedValues.completionDate ?? new Date().toISOString().slice(0, 10),
      expiresAtDate: watchedValues.expiresAtDate ?? "",
    });
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight text-text-main">
              Issue Individual Certificate
            </h1>
            <Badge variant="navy">Immutable Version Issuance</Badge>
          </div>
          <p className="mt-1 text-sm text-text-secondary">
            Generate a signed PDF and PNG certificate anchored to a published template version with
            an embedded verification QR code.
          </p>
        </div>

        <div className="flex items-center gap-2">
          <Link href="/certificates">
            <Button variant="outline">All Certificates</Button>
          </Link>
          <Link href="/studio/templates">
            <Button variant="outline">Template Gallery</Button>
          </Link>
        </div>
      </div>

      {templatesLoading ? (
        <StatePanel
          variant="loading"
          title="Loading published templates"
          description="Fetching published templates ready for issuance..."
        />
      ) : templatesError ? (
        <StatePanel
          variant="error"
          title="Unable to load templates"
          description={templatesError}
          actionLabel="Retry"
          onAction={() => setReloadTick((v) => v + 1)}
        />
      ) : templates.length === 0 ? (
        <StatePanel
          variant="empty"
          title="No published templates available"
          description="Publish a certificate template in Certificate Studio before issuing certificates."
          actionLabel="Go to Template Gallery"
          onAction={() => router.push("/studio/templates")}
        />
      ) : (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,460px)_1fr]">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-lg">
                <Sparkles className="h-4 w-4 text-brand-navy" />
                Recipient & Program Details
              </CardTitle>
              <CardDescription>
                Issued certificates snapshot the currently published template version.
              </CardDescription>
            </CardHeader>
            <CardContent>
              <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
                <div>
                  <label
                    htmlFor="templateId"
                    className="mb-1.5 block text-sm font-medium text-text-main"
                  >
                    Published Template <span className="text-rose-600">*</span>
                  </label>
                  <select
                    id="templateId"
                    {...register("templateId")}
                    className="h-10 w-full rounded-lg border border-border-subtle bg-white px-3 text-sm text-text-main shadow-xs focus:border-brand-navy focus:outline-none"
                  >
                    {templates.map((tpl) => (
                      <option key={tpl.id} value={tpl.id}>
                        {tpl.name} (v{tpl.publishedVersion} · {tpl.orientation})
                      </option>
                    ))}
                  </select>
                  {errors.templateId ? (
                    <p className="mt-1 text-xs text-rose-600">{errors.templateId.message}</p>
                  ) : null}
                </div>

                <div>
                  <label
                    htmlFor="recipientName"
                    className="mb-1.5 block text-sm font-medium text-text-main"
                  >
                    Recipient Full Name <span className="text-rose-600">*</span>
                  </label>
                  <Input
                    id="recipientName"
                    placeholder="e.g., Sokha Dara or Alex Rivera"
                    {...register("recipientName")}
                  />
                  {errors.recipientName ? (
                    <p className="mt-1 text-xs text-rose-600">{errors.recipientName.message}</p>
                  ) : null}
                </div>

                <div>
                  <label
                    htmlFor="recipientEmail"
                    className="mb-1.5 block text-sm font-medium text-text-main"
                  >
                    Recipient Email
                  </label>
                  <Input
                    id="recipientEmail"
                    type="email"
                    placeholder="recipient@organization.org"
                    {...register("recipientEmail")}
                  />
                  {errors.recipientEmail ? (
                    <p className="mt-1 text-xs text-rose-600">{errors.recipientEmail.message}</p>
                  ) : null}
                </div>

                <div>
                  <label
                    htmlFor="courseName"
                    className="mb-1.5 block text-sm font-medium text-text-main"
                  >
                    Course / Achievement Title <span className="text-rose-600">*</span>
                  </label>
                  <Input
                    id="courseName"
                    placeholder="e.g., Cloud Security Architecture Cohort"
                    {...register("courseName")}
                  />
                  {errors.courseName ? (
                    <p className="mt-1 text-xs text-rose-600">{errors.courseName.message}</p>
                  ) : null}
                </div>

                <div className="grid gap-3 sm:grid-cols-2">
                  <div>
                    <label
                      htmlFor="completionDate"
                      className="mb-1.5 block text-sm font-medium text-text-main"
                    >
                      Completion Date
                    </label>
                    <Input id="completionDate" type="date" {...register("completionDate")} />
                  </div>
                  <div>
                    <label
                      htmlFor="expiresAtDate"
                      className="mb-1.5 block text-sm font-medium text-text-main"
                    >
                      Expiration Date (Optional)
                    </label>
                    <Input id="expiresAtDate" type="date" {...register("expiresAtDate")} />
                  </div>
                </div>

                <div className="rounded-lg border border-border-subtle bg-slate-50/70 p-3">
                  <div className="flex items-center justify-between gap-2">
                    <div>
                      <p className="text-xs font-semibold tracking-wide text-text-main uppercase">
                        Custom Placeholder Metadata
                      </p>
                      <p className="text-xs text-text-secondary">
                        Optional key/value pairs for custom placeholders like{" "}
                        <code className="font-mono">{"{{instructor}}"}</code>.
                      </p>
                    </div>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() =>
                        setMetadataRows((prev) => [
                          ...prev,
                          { id: createIdempotencyKey(), key: "", value: "" },
                        ])
                      }
                    >
                      <Plus className="h-3.5 w-3.5" />
                      Add Field
                    </Button>
                  </div>

                  {metadataRows.length > 0 ? (
                    <div className="mt-3 space-y-2">
                      {metadataRows.map((row) => (
                        <div key={row.id} className="flex items-center gap-2">
                          <Input
                            value={row.key}
                            placeholder="key (e.g. grade)"
                            onChange={(event) =>
                              setMetadataRows((prev) =>
                                prev.map((item) =>
                                  item.id === row.id ? { ...item, key: event.target.value } : item
                                )
                              )
                            }
                          />
                          <Input
                            value={row.value}
                            placeholder="value (e.g. Distinction)"
                            onChange={(event) =>
                              setMetadataRows((prev) =>
                                prev.map((item) =>
                                  item.id === row.id ? { ...item, value: event.target.value } : item
                                )
                              )
                            }
                          />
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            aria-label="Remove metadata field"
                            onClick={() =>
                              setMetadataRows((prev) => prev.filter((item) => item.id !== row.id))
                            }
                          >
                            <Trash2 className="h-4 w-4 text-text-secondary" />
                          </Button>
                        </div>
                      ))}
                    </div>
                  ) : null}
                </div>

                <div className="rounded-lg border border-border-subtle bg-white px-3 py-2.5 text-xs">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-medium text-text-secondary">Idempotency Key</span>
                    <button
                      type="button"
                      onClick={() => setIdempotencyKey(createIdempotencyKey())}
                      className="inline-flex items-center gap-1 text-brand-blue hover:underline"
                    >
                      <RefreshCw className="h-3 w-3" />
                      Rotate Key
                    </button>
                  </div>
                  <p className="mt-1 truncate font-mono text-[11px] text-text-main">
                    {idempotencyKey}
                  </p>
                </div>

                {submitError ? (
                  <p
                    role="alert"
                    className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700"
                  >
                    {submitError}
                  </p>
                ) : null}

                <div className="flex flex-wrap items-center gap-2 pt-1">
                  <Button type="submit" variant="primary" disabled={submitting}>
                    {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                    Issue Certificate
                  </Button>
                  {issuedCertificate ? (
                    <Button type="button" variant="outline" onClick={handleResetForAnother}>
                      Issue Another
                    </Button>
                  ) : null}
                </div>
              </form>
            </CardContent>
          </Card>

          <div className="space-y-6">
            {issuedCertificate ? (
              <Card className="border-emerald-200 bg-emerald-50/40">
                <CardHeader className="pb-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <CheckCircle2 className="h-5 w-5 text-emerald-600" />
                      <CardTitle className="text-base text-emerald-950">
                        Certificate Issued Successfully
                      </CardTitle>
                    </div>
                    <CertificateStatusBadge status={issuedCertificate.status} />
                  </div>
                  <CardDescription className="text-emerald-900/80">
                    Certificate number{" "}
                    <span className="font-mono font-semibold text-emerald-950">
                      {issuedCertificate.certificateNumber}
                    </span>{" "}
                    has been generated and stored in your private organization vault.
                  </CardDescription>
                </CardHeader>
                <CardContent className="flex flex-wrap items-center gap-2">
                  <Button
                    type="button"
                    variant="primary"
                    size="sm"
                    disabled={downloadingFormat !== null || !issuedCertificate.pdfAvailable}
                    onClick={() => handleDownloadIssued("PDF")}
                  >
                    {downloadingFormat === "PDF" ? (
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    ) : (
                      <Download className="h-3.5 w-3.5" />
                    )}
                    Download PDF
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={downloadingFormat !== null || !issuedCertificate.pngAvailable}
                    onClick={() => handleDownloadIssued("PNG")}
                  >
                    {downloadingFormat === "PNG" ? (
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    ) : (
                      <ImageIcon className="h-3.5 w-3.5" />
                    )}
                    Download PNG
                  </Button>
                  <Link href={`/certificates/${issuedCertificate.id}`}>
                    <Button type="button" variant="outline" size="sm">
                      <ExternalLink className="h-3.5 w-3.5" />
                      View Certificate Detail
                    </Button>
                  </Link>
                </CardContent>
              </Card>
            ) : null}

            <Card>
              <CardHeader>
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <CardTitle className="text-base">Published Template Preview</CardTitle>
                    <CardDescription>
                      {selectedTemplate
                        ? `Rendering published version v${selectedTemplate.publishedVersion} (${selectedTemplate.pageWidth} × ${selectedTemplate.pageHeight} pt)`
                        : "Select a template to preview placeholders."}
                    </CardDescription>
                  </div>
                  {designLoading ? (
                    <span className="inline-flex items-center gap-1.5 text-xs text-text-secondary">
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                      Loading version...
                    </span>
                  ) : null}
                </div>
              </CardHeader>
              <CardContent>
                <CertificatePreviewCanvas
                  design={effectiveDesign}
                  assetUrls={selectedTemplate?.assetUrls ?? {}}
                  resolvedPlaceholders={previewPlaceholders}
                />
                <p className="mt-3 text-xs text-text-secondary">
                  Note: The final QR verification code is embedded dynamically during backend PDF
                  and 150 DPI PNG generation.
                </p>
              </CardContent>
            </Card>
          </div>
        </div>
      )}
    </div>
  );
}

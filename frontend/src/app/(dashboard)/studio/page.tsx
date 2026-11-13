"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowRight, FilePlus2, FolderKanban, Loader2, Palette, Sparkles } from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { RequireAuthPanel } from "@/components/auth/require-auth-panel";
import { CertificatePreviewCanvas } from "@/components/templates/certificate-preview-canvas";
import { TemplateStatusBadge } from "@/components/templates/template-status-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { StatePanel } from "@/components/ui/state-panel";
import { createTemplate, listTemplates } from "@/lib/api/templates";
import { ApiClientError, type TemplateOrientation, type TemplateSummary } from "@/lib/api/types";

function StudioHubContent() {
  const router = useRouter();
  const { hasPermission } = useAuth();
  const canRead = hasPermission("template:read");
  const canWrite = hasPermission("template:write");

  const [recentTemplates, setRecentTemplates] = React.useState<TemplateSummary[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [creating, setCreating] = React.useState(false);
  const [quickName, setQuickName] = React.useState("");
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!canRead) return;
    const controller = new AbortController();
    let active = true;

    listTemplates({ page: 0, size: 6 }, controller.signal)
      .then((res) => {
        if (!active) return;
        setRecentTemplates(res.data);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (!active || (err instanceof DOMException && err.name === "AbortError")) {
          return;
        }
        setError(err instanceof ApiClientError ? err.message : "Failed to load studio templates.");
        setLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [canRead]);

  const handleQuickCreate = async (orientation: TemplateOrientation) => {
    if (!canWrite) return;
    setCreating(true);
    setError(null);
    try {
      const label = orientation === "LANDSCAPE" ? "Landscape" : "Portrait";
      const res = await createTemplate({
        name: quickName.trim() || `Untitled ${label} Certificate`,
        category: "General",
        orientation,
      });
      router.push(`/studio/templates/${res.data.id}`);
    } catch (err) {
      setError(err instanceof ApiClientError ? err.message : "Failed to create template draft.");
      setCreating(false);
    }
  };

  if (!canRead) {
    return (
      <StatePanel
        variant="error"
        title="Studio access restricted"
        description="Your current role does not include template:read permission."
      />
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-text-main">
            Certificate Studio
          </h1>
          <p className="mt-1 text-sm text-text-secondary">
            Launch an interactive Fabric.js canvas backed by canonical versioned JSON and English /
            Khmer typography.
          </p>
        </div>
        <Link
          href="/studio/templates"
          className="inline-flex h-9 items-center gap-2 rounded-lg border border-border-subtle bg-surface px-3.5 text-sm font-medium text-text-main transition-colors hover:bg-slate-50"
        >
          <FolderKanban className="h-4 w-4 text-brand-navy" />
          Browse Template Gallery
        </Link>
      </div>

      {error ? (
        <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
          {error}
        </div>
      ) : null}

      {canWrite ? (
        <Card className="border-brand-navy/20">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-brand-gold" />
              Start a New Design
            </CardTitle>
            <CardDescription>
              Create a new draft pre-loaded with Navy/Gold framing, dynamic placeholders, and a QR
              verification block.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="max-w-md">
              <label className="mb-1 block text-xs font-medium text-text-main">
                Template Name (optional)
              </label>
              <Input
                value={quickName}
                onChange={(e) => setQuickName(e.target.value)}
                placeholder="e.g. Data Science Specialization Certificate"
              />
            </div>
            <div className="flex flex-wrap gap-3">
              <Button
                type="button"
                disabled={creating}
                onClick={() => void handleQuickCreate("LANDSCAPE")}
              >
                {creating ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <FilePlus2 className="h-4 w-4" />
                )}
                New A4 Landscape (842 × 595 pt)
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={creating}
                onClick={() => void handleQuickCreate("PORTRAIT")}
              >
                <Palette className="h-4 w-4" />
                New A4 Portrait (595 × 842 pt)
              </Button>
            </div>
          </CardContent>
        </Card>
      ) : null}

      <div>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-base font-semibold text-text-main">Recent Designs</h2>
          <Link
            href="/studio/designs"
            className="text-xs font-medium text-brand-blue hover:underline"
          >
            View Drafts Only →
          </Link>
        </div>

        {loading ? (
          <StatePanel
            variant="loading"
            title="Loading recent templates"
            description="Fetching your organization certificate designs."
          />
        ) : recentTemplates.length === 0 ? (
          <StatePanel
            variant="empty"
            title="No designs yet"
            description="Create a landscape or portrait starter template above to open the Certificate Studio canvas."
          />
        ) : (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {recentTemplates.map((item) => (
              <Card key={item.id} className="overflow-hidden">
                <div className="border-b border-border-subtle bg-slate-50/70 p-3">
                  <CertificatePreviewCanvas
                    design={item.previewDesign}
                    assetUrls={item.assetUrls}
                    className="mx-auto max-h-44"
                  />
                </div>
                <CardContent className="flex items-center justify-between gap-3 pt-4">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold text-text-main">{item.name}</p>
                    <div className="mt-1 flex items-center gap-1.5">
                      <TemplateStatusBadge status={item.status} />
                      <span className="text-xs text-text-secondary">v{item.currentVersion}</span>
                    </div>
                  </div>
                  <Link
                    href={`/studio/templates/${item.id}`}
                    className="inline-flex h-8 shrink-0 items-center gap-1 rounded-lg bg-brand-navy px-3 text-xs font-medium text-white hover:bg-brand-navy-hover"
                  >
                    Open Studio
                    <ArrowRight className="h-3.5 w-3.5" />
                  </Link>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

export default function StudioPage() {
  return (
    <RequireAuthPanel>
      <StudioHubContent />
    </RequireAuthPanel>
  );
}

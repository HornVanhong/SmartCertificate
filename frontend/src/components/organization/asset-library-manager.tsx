"use client";

import * as React from "react";
import { CheckCircle2, ImagePlus, Loader2, RefreshCw } from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { StatePanel } from "@/components/ui/state-panel";
import { resolveBackendAssetUrl } from "@/lib/api/client";
import {
  fetchCurrentOrganization,
  listOrganizationAssets,
  updateCurrentOrganization,
  uploadOrganizationAsset,
} from "@/lib/api/organizations";
import {
  ApiClientError,
  type AssetType,
  type MediaAsset,
  type OrganizationProfile,
} from "@/lib/api/types";

interface AssetLibraryManagerProps {
  title: string;
  description: string;
  allowedTypes: AssetType[];
  defaultType: AssetType;
  allowSetAsLogo?: boolean;
}

export function AssetLibraryManager({
  title,
  description,
  allowedTypes,
  defaultType,
  allowSetAsLogo = false,
}: AssetLibraryManagerProps) {
  const { hasPermission } = useAuth();
  const canUpload = hasPermission("org:assets:upload");
  const canUpdateOrg = hasPermission("org:update");

  const [selectedType, setSelectedType] = React.useState<AssetType>(defaultType);
  const [assets, setAssets] = React.useState<MediaAsset[]>([]);
  const [org, setOrg] = React.useState<OrganizationProfile | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [uploading, setUploading] = React.useState(false);
  const [settingLogoId, setSettingLogoId] = React.useState<string | null>(null);
  const [feedback, setFeedback] = React.useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);
  const [reloadTick, setReloadTick] = React.useState(0);
  const fileInputRef = React.useRef<HTMLInputElement | null>(null);

  React.useEffect(() => {
    const controller = new AbortController();
    let active = true;

    Promise.all([
      listOrganizationAssets({ page: 0, size: 50 }, controller.signal),
      allowSetAsLogo ? fetchCurrentOrganization(controller.signal) : Promise.resolve(null),
    ])
      .then(([assetsRes, orgRes]) => {
        if (!active) return;
        const filtered = assetsRes.data.filter((a) => allowedTypes.includes(a.assetType));
        setAssets(filtered);
        if (orgRes) {
          setOrg(orgRes.data);
        }
        setError(null);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (!active || (err instanceof DOMException && err.name === "AbortError")) {
          return;
        }
        setError(
          err instanceof ApiClientError ? err.message : "Failed to load organization assets."
        );
        setLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [reloadTick, allowedTypes, allowSetAsLogo]);

  const triggerReload = () => {
    setLoading(true);
    setReloadTick((t) => t + 1);
  };

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;

    setUploading(true);
    setFeedback(null);
    try {
      const response = await uploadOrganizationAsset(file, selectedType);
      setAssets((prev) => [response.data, ...prev]);
      setFeedback({
        type: "success",
        text: `Uploaded ${response.data.originalFileName} (${response.data.width}x${response.data.height}px).`,
      });
    } catch (err) {
      setFeedback({
        type: "error",
        text: err instanceof ApiClientError ? err.message : "Asset upload failed.",
      });
    } finally {
      setUploading(false);
    }
  };

  const handleSetAsLogo = async (assetId: string) => {
    if (!org) return;
    setSettingLogoId(assetId);
    setFeedback(null);
    try {
      const updated = await updateCurrentOrganization({
        name: org.name,
        logoAssetId: assetId,
        contactEmail: org.contactEmail ?? "",
        phone: org.phone ?? "",
        address: org.address ?? "",
        website: org.website ?? "",
        brandSettings: org.brandSettings,
      });
      setOrg(updated.data);
      setFeedback({ type: "success", text: "Organization logo updated." });
    } catch (err) {
      setFeedback({
        type: "error",
        text: err instanceof ApiClientError ? err.message : "Failed to update organization logo.",
      });
    } finally {
      setSettingLogoId(null);
    }
  };

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-text-main">{title}</h1>
          <p className="mt-1 text-sm text-text-secondary">{description}</p>
        </div>
        <Button variant="outline" size="sm" onClick={triggerReload} disabled={loading}>
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} />
          Refresh
        </Button>
      </div>

      {feedback ? (
        <div
          role={feedback.type === "error" ? "alert" : "status"}
          className={`rounded-lg border px-4 py-2.5 text-sm ${
            feedback.type === "error"
              ? "border-rose-200 bg-rose-50 text-rose-700"
              : "border-emerald-200 bg-emerald-50 text-emerald-800"
          }`}
        >
          {feedback.text}
        </div>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>Upload Asset</CardTitle>
          <CardDescription>
            Accepted formats: PNG, JPEG, or WebP (16x16 to 4096x4096 px, max 5 MB). Stored privately
            with short-lived signed URLs.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap items-center gap-3">
          {allowedTypes.length > 1 ? (
            <select
              aria-label="Asset type"
              value={selectedType}
              onChange={(e) => setSelectedType(e.target.value as AssetType)}
              disabled={!canUpload || uploading}
              className="h-9 rounded-lg border border-border-subtle bg-surface px-3 text-sm text-text-main focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue"
            >
              {allowedTypes.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          ) : null}

          <input
            ref={fileInputRef}
            type="file"
            accept="image/png,image/jpeg,image/webp"
            onChange={(e) => void handleFileChange(e)}
            className="hidden"
          />

          <Button onClick={() => fileInputRef.current?.click()} disabled={!canUpload || uploading}>
            {uploading ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <ImagePlus className="h-4 w-4" />
            )}
            {uploading ? "Validating & Uploading..." : `Upload ${selectedType}`}
          </Button>

          {!canUpload ? (
            <span className="text-xs text-text-secondary">
              Only organization administrators can upload assets.
            </span>
          ) : null}
        </CardContent>
      </Card>

      {loading ? (
        <StatePanel
          variant="loading"
          title="Loading asset library"
          description="Fetching validated media assets for your organization..."
        />
      ) : error ? (
        <StatePanel
          variant="error"
          title="Unable to load assets"
          description={error}
          actionLabel="Retry"
          onAction={triggerReload}
        />
      ) : assets.length === 0 ? (
        <StatePanel
          variant="empty"
          title="No assets uploaded yet"
          description="Upload a PNG, JPEG, or WebP asset above to start building your organization's media library."
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {assets.map((asset) => {
            const previewUrl = resolveBackendAssetUrl(asset.signedUrl);
            const isActiveLogo = org?.logoAssetId === asset.id;

            return (
              <Card key={asset.id} className="overflow-hidden">
                <div className="flex h-40 items-center justify-center border-b border-border-subtle bg-slate-50 p-4">
                  {previewUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={previewUrl}
                      alt={asset.originalFileName}
                      className="max-h-full max-w-full object-contain"
                    />
                  ) : null}
                </div>
                <CardContent className="space-y-3 p-4">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-semibold text-text-main">
                        {asset.originalFileName}
                      </p>
                      <p className="text-xs text-text-secondary">
                        {asset.width && asset.height ? `${asset.width}x${asset.height}px · ` : ""}
                        {(asset.fileSize / 1024).toFixed(1)} KB
                      </p>
                    </div>
                    <Badge variant={isActiveLogo ? "gold" : "neutral"}>{asset.assetType}</Badge>
                  </div>

                  {allowSetAsLogo && asset.assetType === "LOGO" ? (
                    isActiveLogo ? (
                      <div className="inline-flex items-center gap-1.5 text-xs font-medium text-emerald-700">
                        <CheckCircle2 className="h-3.5 w-3.5" />
                        Active Organization Logo
                      </div>
                    ) : (
                      <Button
                        variant="outline"
                        size="sm"
                        className="w-full"
                        disabled={!canUpdateOrg || settingLogoId === asset.id}
                        onClick={() => void handleSetAsLogo(asset.id)}
                      >
                        {settingLogoId === asset.id ? (
                          <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        ) : null}
                        Set as Organization Logo
                      </Button>
                    )
                  ) : null}
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}

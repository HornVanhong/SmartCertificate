"use client";

import * as React from "react";
import Link from "next/link";
import {
  ArrowDown,
  ArrowUp,
  BadgeCheck,
  Braces,
  Circle,
  Copy,
  Eye,
  EyeOff,
  Image as ImageIcon,
  Layers,
  LayoutTemplate,
  Lock,
  Minus,
  PaintBucket,
  PenTool,
  QrCode,
  Shapes,
  Square,
  Stamp,
  Trash2,
  Type,
  Unlock,
} from "lucide-react";
import { CertificatePreviewCanvas } from "@/components/templates/certificate-preview-canvas";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { resolveBackendAssetUrl } from "@/lib/api/client";
import type {
  AssetType,
  DesignDocument,
  DesignElement,
  DesignElementType,
  DesignPage,
  MediaAsset,
} from "@/lib/api/types";
import {
  DYNAMIC_PLACEHOLDERS,
  normalizeHexColor,
  type CreateElementOptions,
} from "@/lib/editor/design-helpers";
import { CERTIFICATE_STUDIO_PRESETS, type CertificatePreset } from "@/lib/editor/design-presets";

interface EditorLeftPanelProps {
  canEdit: boolean;
  page: DesignPage;
  elements: DesignElement[];
  selectedElementId: string | null;
  assets: MediaAsset[];
  assetsLoading: boolean;
  onApplyPreset: (preset: CertificatePreset) => void;
  onUpdatePage: (patch: Partial<DesignPage>) => void;
  onAddElement: (type: DesignElementType, options?: CreateElementOptions) => void;
  onInsertPlaceholderToken: (token: string, label: string) => void;
  onInsertAsset: (asset: MediaAsset) => void;
  onSetBackgroundAsset: (asset: MediaAsset) => void;
  onSelectElement: (elementId: string) => void;
  onToggleVisibility: (elementId: string) => void;
  onToggleLock: (elementId: string) => void;
  onReorderLayer: (elementId: string, direction: "up" | "down") => void;
  onDuplicateElement: (elementId: string) => void;
  onDeleteElement: (elementId: string) => void;
}

type StudioRailTab =
  "templates" | "background" | "elements" | "texts" | "attributes" | "qr" | "layers";

const RAIL_TABS: Array<{ id: StudioRailTab; label: string; icon: typeof LayoutTemplate }> = [
  { id: "templates", label: "Templates", icon: LayoutTemplate },
  { id: "background", label: "Background", icon: PaintBucket },
  { id: "elements", label: "Elements", icon: Shapes },
  { id: "texts", label: "Texts", icon: Type },
  { id: "attributes", label: "Attributes", icon: Braces },
  { id: "qr", label: "QR Code", icon: QrCode },
  { id: "layers", label: "Layers", icon: Layers },
];

const BACKGROUND_SWATCHES = [
  { label: "Pure White", hex: "#FFFFFF" },
  { label: "Warm Ivory", hex: "#FCFBF7" },
  { label: "Soft Cream", hex: "#FBF9F4" },
  { label: "Slate Mist", hex: "#F8FAFC" },
  { label: "Royal Navy", hex: "#142D4E" },
  { label: "Midnight Obsidian", hex: "#0F172A" },
  { label: "Emerald Heritage", hex: "#0F5132" },
] as const;

const ASSET_ELEMENT_TYPE_MAP: Record<AssetType, DesignElementType> = {
  LOGO: "LOGO",
  SIGNATURE: "SIGNATURE",
  STAMP: "STAMP",
  GENERAL: "IMAGE",
};

export function EditorLeftPanel({
  canEdit,
  page,
  elements,
  selectedElementId,
  assets,
  assetsLoading,
  onApplyPreset,
  onUpdatePage,
  onAddElement,
  onInsertPlaceholderToken,
  onInsertAsset,
  onSetBackgroundAsset,
  onSelectElement,
  onToggleVisibility,
  onToggleLock,
  onReorderLayer,
  onDuplicateElement,
  onDeleteElement,
}: EditorLeftPanelProps) {
  const [activeTab, setActiveTab] = React.useState<StudioRailTab>("templates");
  const [styleFilter, setStyleFilter] = React.useState<string>("ALL");
  const [assetFilter, setAssetFilter] = React.useState<AssetType | "ALL">("ALL");

  const sortedLayers = React.useMemo(
    () => [...elements].sort((a, b) => b.zIndex - a.zIndex),
    [elements]
  );

  const filteredAssets = React.useMemo(
    () => (assetFilter === "ALL" ? assets : assets.filter((a) => a.assetType === assetFilter)),
    [assets, assetFilter]
  );

  const filteredPresets = React.useMemo(() => {
    if (styleFilter === "ALL") return CERTIFICATE_STUDIO_PRESETS;
    return CERTIFICATE_STUDIO_PRESETS.filter((preset) => preset.styleTag === styleFilter);
  }, [styleFilter]);

  return (
    <aside className="flex h-full overflow-hidden rounded-xl border border-border-subtle bg-surface shadow-2xs">
      {/* Left Icon Rail inspired by reference Certifier Studio */}
      <div className="flex w-[74px] shrink-0 flex-col items-center gap-1 border-r border-border-subtle bg-slate-50/80 px-1.5 py-2.5">
        {RAIL_TABS.map((tab) => {
          const Icon = tab.icon;
          const active = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => setActiveTab(tab.id)}
              className={`flex w-full flex-col items-center justify-center gap-1 rounded-lg border px-1 py-2 text-[10px] font-medium transition-all ${
                active
                  ? "border-brand-blue/40 bg-brand-blue/10 text-brand-blue shadow-2xs"
                  : "border-transparent text-text-secondary hover:bg-slate-200/50 hover:text-text-main"
              }`}
            >
              <Icon className="h-4 w-4" />
              <span className="truncate">{tab.label}</span>
            </button>
          );
        })}
      </div>

      {/* Contextual Drawer */}
      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <div className="flex items-center justify-between border-b border-border-subtle px-3.5 py-2.5">
          <h2 className="text-xs font-semibold tracking-wide text-text-main uppercase">
            {RAIL_TABS.find((t) => t.id === activeTab)?.label}
          </h2>
          {activeTab === "layers" ? <Badge variant="neutral">{elements.length}</Badge> : null}
        </div>

        <div className="flex-1 overflow-y-auto p-3">
          {activeTab === "templates" ? (
            <div className="space-y-3">
              <p className="text-[11px] leading-relaxed text-text-secondary">
                Click any curated layout to populate your canvas. You can undo anytime.
              </p>

              <div className="flex flex-wrap gap-1">
                {["ALL", "Classic", "Luxury", "Modern", "Minimal"].map((style) => (
                  <button
                    key={style}
                    type="button"
                    onClick={() => setStyleFilter(style)}
                    className={`rounded-md px-2 py-1 text-[10px] font-medium transition-colors ${
                      styleFilter === style
                        ? "bg-brand-navy text-white"
                        : "bg-slate-100 text-text-secondary hover:text-text-main"
                    }`}
                  >
                    {style}
                  </button>
                ))}
              </div>

              <div className="space-y-2.5">
                {filteredPresets.map((preset) => {
                  const previewDoc: DesignDocument = preset.build(page.orientation);
                  return (
                    <button
                      key={preset.id}
                      type="button"
                      disabled={!canEdit}
                      onClick={() => onApplyPreset(preset)}
                      className="group w-full overflow-hidden rounded-xl border border-border-subtle bg-surface text-left transition-all hover:border-brand-blue/60 hover:shadow-sm disabled:opacity-50"
                    >
                      <div className="border-b border-border-subtle bg-slate-100/80 p-2">
                        <CertificatePreviewCanvas
                          design={previewDoc}
                          resolvedPlaceholders={{
                            recipientName: "Sok Dara",
                            courseName: "Cloud Security",
                            organizationName: "SmartCert Academy",
                            issuedDate: "Nov 12, 2026",
                            certificateNumber: "SC-2026-001",
                            issuerName: "Dr. Sokha",
                          }}
                          className="mx-auto max-h-28"
                        />
                      </div>
                      <div className="p-2.5">
                        <div className="flex items-center justify-between gap-1.5">
                          <span className="truncate text-xs font-semibold text-text-main group-hover:text-brand-blue">
                            {preset.name}
                          </span>
                          <Badge variant="neutral" className="px-1.5 py-0 text-[10px]">
                            {preset.styleTag}
                          </Badge>
                        </div>
                        <p className="mt-0.5 line-clamp-2 text-[11px] text-text-secondary">
                          {preset.description}
                        </p>
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>
          ) : null}

          {activeTab === "background" ? (
            <div className="space-y-4">
              <div>
                <p className="mb-2 text-[11px] font-semibold tracking-wider text-text-secondary uppercase">
                  Orientation (A4)
                </p>
                <div className="grid grid-cols-2 gap-2">
                  {(
                    [
                      {
                        value: "LANDSCAPE",
                        label: "Landscape",
                        dims: "842 × 595 pt",
                        w: 842,
                        h: 595,
                      },
                      {
                        value: "PORTRAIT",
                        label: "Portrait",
                        dims: "595 × 842 pt",
                        w: 595,
                        h: 842,
                      },
                    ] as const
                  ).map((opt) => {
                    const active = page.orientation === opt.value;
                    return (
                      <button
                        key={opt.value}
                        type="button"
                        disabled={!canEdit}
                        onClick={() =>
                          onUpdatePage({
                            orientation: opt.value,
                            width: opt.w,
                            height: opt.h,
                          })
                        }
                        className={`rounded-lg border p-2 text-left text-xs transition-colors ${
                          active
                            ? "border-brand-blue bg-brand-blue/10 text-brand-navy"
                            : "border-border-subtle text-text-secondary hover:bg-slate-50"
                        }`}
                      >
                        <p className="font-semibold">{opt.label}</p>
                        <p className="text-[10px] opacity-80">{opt.dims}</p>
                      </button>
                    );
                  })}
                </div>
              </div>

              <div>
                <p className="mb-2 text-[11px] font-semibold tracking-wider text-text-secondary uppercase">
                  Canvas Color Palette
                </p>
                <div className="grid grid-cols-4 gap-2">
                  {BACKGROUND_SWATCHES.map((swatch) => {
                    const selected =
                      normalizeHexColor(page.backgroundColor, "#FFFFFF") === swatch.hex;
                    return (
                      <button
                        key={swatch.hex}
                        type="button"
                        title={swatch.label}
                        disabled={!canEdit}
                        onClick={() => onUpdatePage({ backgroundColor: swatch.hex })}
                        className={`h-8 rounded-lg border transition-transform hover:scale-105 ${
                          selected
                            ? "border-brand-blue ring-2 ring-brand-blue/30"
                            : "border-border-subtle"
                        }`}
                        style={{ backgroundColor: swatch.hex }}
                      />
                    );
                  })}
                </div>

                <div className="mt-3 flex items-center gap-2">
                  <input
                    type="color"
                    aria-label="Custom background hex"
                    disabled={!canEdit}
                    value={normalizeHexColor(page.backgroundColor, "#FFFFFF")}
                    onChange={(e) =>
                      onUpdatePage({ backgroundColor: e.target.value.toUpperCase() })
                    }
                    className="h-8 w-10 cursor-pointer rounded border border-border-subtle bg-surface p-0.5"
                  />
                  <Input
                    value={page.backgroundColor}
                    disabled={!canEdit}
                    onChange={(e) => onUpdatePage({ backgroundColor: e.target.value })}
                    className="h-8 font-mono text-xs"
                  />
                </div>
              </div>

              <div>
                <div className="mb-2 flex items-center justify-between">
                  <p className="text-[11px] font-semibold tracking-wider text-text-secondary uppercase">
                    Background Asset
                  </p>
                  {page.backgroundAssetId && canEdit ? (
                    <button
                      type="button"
                      onClick={() => onUpdatePage({ backgroundAssetId: null })}
                      className="text-[11px] font-medium text-rose-600 hover:underline"
                    >
                      Clear
                    </button>
                  ) : null}
                </div>
                {assets.length === 0 ? (
                  <Link
                    href="/organization/branding"
                    className="block rounded-lg border border-dashed border-border-subtle p-3 text-center text-[11px] font-medium text-brand-blue hover:bg-slate-50"
                  >
                    Upload custom PNG/JPEG in Branding →
                  </Link>
                ) : (
                  <div className="space-y-1.5">
                    {assets.slice(0, 6).map((asset) => (
                      <button
                        key={asset.id}
                        type="button"
                        disabled={!canEdit}
                        onClick={() => onSetBackgroundAsset(asset)}
                        className="flex w-full items-center justify-between rounded-lg border border-border-subtle px-2.5 py-2 text-left text-xs hover:bg-slate-50"
                      >
                        <span className="truncate font-medium text-text-main">
                          {asset.originalFileName}
                        </span>
                        <span className="text-[10px] text-brand-blue">Apply</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ) : null}

          {activeTab === "elements" ? (
            <div className="space-y-4">
              <div>
                <p className="mb-2 text-[11px] font-semibold tracking-wider text-text-secondary uppercase">
                  Vector Shapes
                </p>
                <div className="grid grid-cols-2 gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={!canEdit}
                    onClick={() => onAddElement("SHAPE", { shapeKind: "RECTANGLE" })}
                    className="justify-start text-xs"
                  >
                    <Square className="h-3.5 w-3.5" />
                    Rectangle
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={!canEdit}
                    onClick={() => onAddElement("SHAPE", { shapeKind: "CIRCLE" })}
                    className="justify-start text-xs"
                  >
                    <Circle className="h-3.5 w-3.5" />
                    Circle
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={!canEdit}
                    onClick={() => onAddElement("SHAPE", { shapeKind: "LINE" })}
                    className="col-span-2 justify-start text-xs"
                  >
                    <Minus className="h-3.5 w-3.5" />
                    Accent / Signature Line
                  </Button>
                </div>
              </div>

              <div>
                <p className="mb-2 text-[11px] font-semibold tracking-wider text-text-secondary uppercase">
                  Placeholders &amp; Media Slots
                </p>
                <div className="grid grid-cols-1 gap-1.5">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={!canEdit}
                    onClick={() => onAddElement("LOGO", { name: "Organization Logo" })}
                    className="justify-start text-xs"
                  >
                    <BadgeCheck className="h-3.5 w-3.5 text-brand-navy" />
                    Logo Slot
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={!canEdit}
                    onClick={() => onAddElement("SIGNATURE", { name: "Authorized Signature" })}
                    className="justify-start text-xs"
                  >
                    <PenTool className="h-3.5 w-3.5 text-brand-navy" />
                    Signature Slot
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={!canEdit}
                    onClick={() => onAddElement("STAMP", { name: "Official Stamp" })}
                    className="justify-start text-xs"
                  >
                    <Stamp className="h-3.5 w-3.5 text-brand-gold" />
                    Official Seal / Stamp
                  </Button>
                </div>
              </div>

              <div>
                <div className="mb-2 flex items-center justify-between">
                  <p className="text-[11px] font-semibold tracking-wider text-text-secondary uppercase">
                    Organization Assets
                  </p>
                  <select
                    aria-label="Filter organization assets"
                    value={assetFilter}
                    onChange={(e) => setAssetFilter(e.target.value as AssetType | "ALL")}
                    className="h-6 rounded border border-border-subtle bg-surface px-1.5 text-[10px]"
                  >
                    <option value="ALL">All</option>
                    <option value="LOGO">Logos</option>
                    <option value="SIGNATURE">Signatures</option>
                    <option value="STAMP">Stamps</option>
                  </select>
                </div>

                {assetsLoading ? (
                  <p className="py-4 text-center text-xs text-text-secondary">Loading assets...</p>
                ) : filteredAssets.length === 0 ? (
                  <div className="rounded-lg border border-dashed border-border-subtle p-3 text-center text-[11px] text-text-secondary">
                    No assets uploaded yet.{" "}
                    <Link
                      href="/organization/branding"
                      className="font-medium text-brand-blue hover:underline"
                    >
                      Upload →
                    </Link>
                  </div>
                ) : (
                  <div className="space-y-2">
                    {filteredAssets.map((asset) => {
                      const previewUrl = resolveBackendAssetUrl(asset.signedUrl);
                      const targetType = ASSET_ELEMENT_TYPE_MAP[asset.assetType];
                      return (
                        <div
                          key={asset.id}
                          className="flex items-center gap-2 rounded-lg border border-border-subtle p-2 text-xs"
                        >
                          <div className="flex h-10 w-10 shrink-0 items-center justify-center overflow-hidden rounded border border-border-subtle bg-slate-50">
                            {previewUrl ? (
                              // eslint-disable-next-line @next/next/no-img-element
                              <img
                                src={previewUrl}
                                alt={asset.originalFileName}
                                className="h-full w-full object-contain"
                              />
                            ) : (
                              <ImageIcon className="h-4 w-4 text-slate-400" />
                            )}
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="truncate font-medium text-text-main">
                              {asset.originalFileName}
                            </p>
                            <button
                              type="button"
                              disabled={!canEdit}
                              onClick={() => onInsertAsset(asset)}
                              className="text-[11px] font-medium text-brand-blue hover:underline disabled:opacity-50"
                            >
                              + Insert {targetType}
                            </button>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>
          ) : null}

          {activeTab === "texts" ? (
            <div className="space-y-2.5">
              <p className="text-[11px] text-text-secondary">
                Add styled English or Khmer typography blocks to the canvas.
              </p>
              <Button
                type="button"
                variant="outline"
                disabled={!canEdit}
                onClick={() =>
                  onAddElement("TEXT", {
                    name: "Certificate Heading",
                    text: "CERTIFICATE OF ACHIEVEMENT",
                    fontSize: 28,
                    fontWeight: "BOLD",
                    fontFamily: "Georgia",
                    height: 48,
                  })
                }
                className="h-auto w-full justify-start py-2.5 text-left"
              >
                <div>
                  <p className="font-serif text-sm font-bold text-text-main">Add Heading</p>
                  <p className="text-[10px] text-text-secondary">Georgia · 28pt Bold</p>
                </div>
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={!canEdit}
                onClick={() =>
                  onAddElement("TEXT", {
                    name: "Subtitle",
                    text: "This certificate is proudly presented to",
                    fontSize: 13,
                    fontFamily: "Inter",
                    height: 28,
                  })
                }
                className="h-auto w-full justify-start py-2 text-left"
              >
                <div>
                  <p className="text-xs font-semibold text-text-main">Add Subtitle</p>
                  <p className="text-[10px] text-text-secondary">Inter · 13pt Regular</p>
                </div>
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={!canEdit}
                onClick={() =>
                  onAddElement("TEXT", {
                    name: "Body Paragraph",
                    text: "For successfully completing all coursework and practical assessments.",
                    fontSize: 13,
                    fontFamily: "Inter",
                    height: 44,
                  })
                }
                className="h-auto w-full justify-start py-2 text-left"
              >
                <div>
                  <p className="text-xs font-medium text-text-main">Add Body Paragraph</p>
                  <p className="text-[10px] text-text-secondary">Inter · 13pt Multiline</p>
                </div>
              </Button>
              <Button
                type="button"
                variant="outline"
                disabled={!canEdit}
                onClick={() =>
                  onAddElement("TEXT", {
                    name: "Khmer Headline",
                    text: "វិញ្ញាបនបត្របញ្ចប់វគ្គសិក្សា",
                    fontSize: 22,
                    fontWeight: "BOLD",
                    fontFamily: "Noto Sans Khmer",
                    height: 46,
                  })
                }
                className="h-auto w-full justify-start border-brand-gold/40 bg-brand-gold/5 py-2.5 text-left hover:bg-brand-gold/10"
              >
                <div>
                  <p className="text-sm font-bold text-brand-navy">វិញ្ញាបនបត្រ (Khmer Title)</p>
                  <p className="text-[10px] text-text-secondary">Noto Sans Khmer · 22pt Bold</p>
                </div>
              </Button>
            </div>
          ) : null}

          {activeTab === "attributes" ? (
            <div className="space-y-3">
              <p className="text-[11px] leading-relaxed text-text-secondary">
                Click any dynamic attribute to insert it or append it to the currently selected text
                block. Replaced automatically during single &amp; bulk CSV issuance.
              </p>
              <div className="space-y-1.5">
                {DYNAMIC_PLACEHOLDERS.map((item) => (
                  <button
                    key={item.key}
                    type="button"
                    disabled={!canEdit}
                    onClick={() => onInsertPlaceholderToken(item.token, item.label)}
                    className="flex w-full items-center justify-between rounded-lg border border-border-subtle bg-slate-50/60 px-3 py-2 text-left transition-colors hover:border-brand-blue/50 hover:bg-brand-blue/5 disabled:opacity-50"
                  >
                    <span className="text-xs font-medium text-text-main">{item.label}</span>
                    <code className="rounded bg-white px-1.5 py-0.5 font-mono text-[10px] text-brand-blue ring-1 ring-brand-blue/20">
                      {item.token}
                    </code>
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          {activeTab === "qr" ? (
            <div className="space-y-3">
              <p className="text-[11px] leading-relaxed text-text-secondary">
                Add a scannable verification QR code. Each issued certificate automatically embeds
                its unique verification URL.
              </p>
              <Button
                type="button"
                variant="primary"
                disabled={!canEdit}
                onClick={() => onAddElement("QR", { name: "Verification QR" })}
                className="w-full"
              >
                <QrCode className="h-4 w-4" />
                Insert Verification QR Code
              </Button>
              <div className="rounded-lg border border-border-subtle bg-slate-50 p-3 text-[11px] text-text-secondary">
                Payload template:{" "}
                <code className="font-mono text-text-main">{"{{verificationUrl}}"}</code>
                <br />
                Rendered using ZXing at high contrast directly in exported PDFs &amp; PNGs.
              </div>
            </div>
          ) : null}

          {activeTab === "layers" ? (
            <div className="space-y-1.5">
              {sortedLayers.length === 0 ? (
                <p className="py-6 text-center text-xs text-text-secondary">
                  No elements on canvas yet. Choose a preset from Templates or add elements.
                </p>
              ) : (
                sortedLayers.map((el) => {
                  const selected = el.id === selectedElementId;
                  return (
                    <div
                      key={el.id}
                      className={`rounded-lg border p-2 text-xs transition-colors ${
                        selected
                          ? "border-brand-blue bg-brand-blue/5"
                          : "border-border-subtle hover:bg-slate-50"
                      }`}
                    >
                      <div className="flex items-center justify-between gap-1.5">
                        <button
                          type="button"
                          onClick={() => onSelectElement(el.id)}
                          className="min-w-0 flex-1 text-left"
                        >
                          <p className="truncate font-medium text-text-main">
                            {el.name || el.type}
                          </p>
                          <p className="text-[10px] text-text-secondary">
                            {el.type} · z{el.zIndex}
                          </p>
                        </button>

                        <div className="flex items-center gap-0.5">
                          <button
                            type="button"
                            title={el.visible ? "Hide layer" : "Show layer"}
                            disabled={!canEdit}
                            onClick={() => onToggleVisibility(el.id)}
                            className="rounded p-1 text-text-secondary hover:bg-slate-100 disabled:opacity-40"
                          >
                            {el.visible ? (
                              <Eye className="h-3.5 w-3.5" />
                            ) : (
                              <EyeOff className="h-3.5 w-3.5 text-slate-400" />
                            )}
                          </button>
                          <button
                            type="button"
                            title={el.locked ? "Unlock layer" : "Lock layer"}
                            disabled={!canEdit}
                            onClick={() => onToggleLock(el.id)}
                            className="rounded p-1 text-text-secondary hover:bg-slate-100 disabled:opacity-40"
                          >
                            {el.locked ? (
                              <Lock className="h-3.5 w-3.5 text-amber-600" />
                            ) : (
                              <Unlock className="h-3.5 w-3.5" />
                            )}
                          </button>
                        </div>
                      </div>

                      {selected && canEdit ? (
                        <div className="mt-2 flex items-center justify-between border-t border-border-subtle/70 pt-1.5">
                          <div className="flex items-center gap-1">
                            <button
                              type="button"
                              title="Bring forward"
                              onClick={() => onReorderLayer(el.id, "up")}
                              className="rounded border border-border-subtle p-1 text-text-secondary hover:bg-slate-100"
                            >
                              <ArrowUp className="h-3 w-3" />
                            </button>
                            <button
                              type="button"
                              title="Send backward"
                              onClick={() => onReorderLayer(el.id, "down")}
                              className="rounded border border-border-subtle p-1 text-text-secondary hover:bg-slate-100"
                            >
                              <ArrowDown className="h-3 w-3" />
                            </button>
                          </div>
                          <div className="flex items-center gap-1">
                            <button
                              type="button"
                              title="Duplicate"
                              onClick={() => onDuplicateElement(el.id)}
                              className="rounded border border-border-subtle p-1 text-text-secondary hover:bg-slate-100"
                            >
                              <Copy className="h-3 w-3" />
                            </button>
                            <button
                              type="button"
                              title="Delete"
                              onClick={() => onDeleteElement(el.id)}
                              className="rounded border border-rose-200 p-1 text-rose-600 hover:bg-rose-50"
                            >
                              <Trash2 className="h-3 w-3" />
                            </button>
                          </div>
                        </div>
                      ) : null}
                    </div>
                  );
                })
              )}
            </div>
          ) : null}
        </div>
      </div>
    </aside>
  );
}

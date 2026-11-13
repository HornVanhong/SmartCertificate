"use client";

import * as React from "react";
import {
  AlignCenterHorizontal,
  AlignCenterVertical,
  AlignEndHorizontal,
  AlignEndVertical,
  AlignStartHorizontal,
  AlignStartVertical,
  Copy,
  Lock,
  Trash2,
  Unlock,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { DesignElement, DesignPage, MediaAsset } from "@/lib/api/types";
import {
  DYNAMIC_PLACEHOLDERS,
  SUPPORTED_CERTIFICATE_FONTS,
  normalizeHexColor,
  type PageAlignment,
} from "@/lib/editor/design-helpers";

interface EditorPropertiesPanelProps {
  canEdit: boolean;
  page: DesignPage;
  selectedElement: DesignElement | null;
  assets: MediaAsset[];
  category: string;
  description: string;
  onMetaChange: (field: "category" | "description", value: string) => void;
  onUpdatePage: (patch: Partial<DesignPage>) => void;
  onUpdateElement: (elementId: string, patch: Partial<DesignElement>) => void;
  onAlignElement: (elementId: string, alignment: PageAlignment) => void;
  onDuplicateElement: (elementId: string) => void;
  onDeleteElement: (elementId: string) => void;
}

export function EditorPropertiesPanel({
  canEdit,
  page,
  selectedElement,
  assets,
  category,
  description,
  onMetaChange,
  onUpdatePage,
  onUpdateElement,
  onAlignElement,
  onDuplicateElement,
  onDeleteElement,
}: EditorPropertiesPanelProps) {
  if (!selectedElement) {
    return (
      <aside className="flex h-full flex-col overflow-y-auto rounded-xl border border-border-subtle bg-surface p-4 text-xs">
        <div className="border-b border-border-subtle pb-3">
          <h2 className="text-sm font-semibold text-text-main">Page & Template Settings</h2>
          <p className="mt-0.5 text-[11px] text-text-secondary">
            Select an element on the canvas or configure page background and template metadata.
          </p>
        </div>

        <div className="mt-4 space-y-4">
          <div>
            <label className="mb-1 block font-medium text-text-main">Category</label>
            <Input
              value={category}
              disabled={!canEdit}
              placeholder="e.g. Completion, Academic, Award"
              onChange={(e) => onMetaChange("category", e.target.value)}
            />
          </div>

          <div>
            <label className="mb-1 block font-medium text-text-main">Description</label>
            <textarea
              rows={3}
              value={description}
              disabled={!canEdit}
              onChange={(e) => onMetaChange("description", e.target.value)}
              placeholder="Internal notes for template publishers and issuers..."
              className="w-full rounded-lg border border-border-subtle bg-surface px-3 py-2 text-xs text-text-main focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue disabled:bg-slate-50"
            />
          </div>

          <div className="rounded-lg border border-border-subtle bg-slate-50/70 p-3">
            <p className="font-medium text-text-main">Dimensions</p>
            <p className="mt-1 text-text-secondary">
              {page.orientation} · {page.width} × {page.height} {page.unit}
            </p>
          </div>

          <div>
            <label className="mb-1 block font-medium text-text-main">Page Background Color</label>
            <div className="flex items-center gap-2">
              <input
                type="color"
                aria-label="Page background color picker"
                disabled={!canEdit}
                value={normalizeHexColor(page.backgroundColor, "#FFFFFF")}
                onChange={(e) => onUpdatePage({ backgroundColor: e.target.value.toUpperCase() })}
                className="h-9 w-11 cursor-pointer rounded border border-border-subtle bg-surface p-0.5"
              />
              <Input
                value={page.backgroundColor}
                disabled={!canEdit}
                onChange={(e) => onUpdatePage({ backgroundColor: e.target.value })}
                className="font-mono"
              />
            </div>
          </div>

          {page.backgroundAssetId ? (
            <div className="flex items-center justify-between rounded-lg border border-border-subtle bg-slate-50 p-2.5">
              <span className="truncate text-text-secondary">Background image active</span>
              {canEdit ? (
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  onClick={() => onUpdatePage({ backgroundAssetId: null })}
                  className="h-7 px-2 text-xs text-rose-600"
                >
                  Clear
                </Button>
              ) : null}
            </div>
          ) : null}
        </div>
      </aside>
    );
  }

  const update = (patch: Partial<DesignElement>) => onUpdateElement(selectedElement.id, patch);

  return (
    <aside className="flex h-full flex-col overflow-y-auto rounded-xl border border-border-subtle bg-surface p-4 text-xs">
      <div className="flex items-center justify-between gap-2 border-b border-border-subtle pb-3">
        <div className="min-w-0 flex-1">
          <p className="text-[10px] font-semibold tracking-wider text-brand-blue uppercase">
            {selectedElement.type}
          </p>
          <Input
            aria-label="Element name"
            disabled={!canEdit}
            value={selectedElement.name ?? ""}
            onChange={(e) => update({ name: e.target.value })}
            className="mt-1 h-7 text-xs font-semibold"
          />
        </div>

        <div className="flex items-center gap-1 pt-4">
          <Button
            type="button"
            variant="outline"
            size="sm"
            title={selectedElement.locked ? "Unlock" : "Lock"}
            disabled={!canEdit}
            onClick={() => update({ locked: !selectedElement.locked })}
            className="h-7 px-2"
          >
            {selectedElement.locked ? (
              <Lock className="h-3.5 w-3.5 text-amber-600" />
            ) : (
              <Unlock className="h-3.5 w-3.5" />
            )}
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            title="Duplicate (Ctrl+D)"
            disabled={!canEdit}
            onClick={() => onDuplicateElement(selectedElement.id)}
            className="h-7 px-2"
          >
            <Copy className="h-3.5 w-3.5" />
          </Button>
          <Button
            type="button"
            variant="outline"
            size="sm"
            title="Delete"
            disabled={!canEdit}
            onClick={() => onDeleteElement(selectedElement.id)}
            className="h-7 px-2 text-rose-600 hover:bg-rose-50"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </Button>
        </div>
      </div>

      <div className="mt-3 space-y-4">
        <div>
          <p className="mb-1.5 text-[11px] font-semibold text-text-secondary uppercase">
            Align on Page
          </p>
          <div className="grid grid-cols-6 gap-1">
            {(
              [
                { align: "left", icon: AlignStartHorizontal, title: "Align left margin" },
                { align: "center-x", icon: AlignCenterHorizontal, title: "Center horizontally" },
                { align: "right", icon: AlignEndHorizontal, title: "Align right margin" },
                { align: "top", icon: AlignStartVertical, title: "Align top margin" },
                { align: "center-y", icon: AlignCenterVertical, title: "Center vertically" },
                { align: "bottom", icon: AlignEndVertical, title: "Align bottom margin" },
              ] as const
            ).map((btn) => {
              const Icon = btn.icon;
              return (
                <button
                  key={btn.align}
                  type="button"
                  title={btn.title}
                  disabled={!canEdit || selectedElement.locked}
                  onClick={() => onAlignElement(selectedElement.id, btn.align)}
                  className="flex h-7 items-center justify-center rounded border border-border-subtle text-text-secondary hover:bg-slate-50 hover:text-text-main disabled:opacity-40"
                >
                  <Icon className="h-3.5 w-3.5" />
                </button>
              );
            })}
          </div>
        </div>

        <div>
          <p className="mb-1.5 text-[11px] font-semibold text-text-secondary uppercase">
            Transform
          </p>
          <div className="grid grid-cols-2 gap-2">
            <NumberField
              label="X (pt)"
              value={selectedElement.x}
              disabled={!canEdit || selectedElement.locked}
              onChange={(v) => update({ x: v })}
            />
            <NumberField
              label="Y (pt)"
              value={selectedElement.y}
              disabled={!canEdit || selectedElement.locked}
              onChange={(v) => update({ y: v })}
            />
            <NumberField
              label="Width"
              value={selectedElement.width}
              min={1}
              disabled={!canEdit || selectedElement.locked}
              onChange={(v) => update({ width: Math.max(1, v) })}
            />
            <NumberField
              label="Height"
              value={selectedElement.height}
              min={1}
              disabled={!canEdit || selectedElement.locked}
              onChange={(v) => update({ height: Math.max(1, v) })}
            />
            <NumberField
              label="Rotation (°)"
              value={selectedElement.rotation}
              disabled={!canEdit || selectedElement.locked}
              onChange={(v) => update({ rotation: Math.max(-360, Math.min(360, v)) })}
            />
            <NumberField
              label="Opacity (%)"
              value={Math.round((selectedElement.opacity ?? 1) * 100)}
              min={0}
              max={100}
              disabled={!canEdit}
              onChange={(v) => update({ opacity: Math.min(1, Math.max(0, v / 100)) })}
            />
          </div>
        </div>

        {selectedElement.type === "TEXT" ? (
          <div className="space-y-3 border-t border-border-subtle pt-3">
            <p className="text-[11px] font-semibold text-text-secondary uppercase">Text & Font</p>
            <div>
              <label className="mb-1 block font-medium text-text-main">Content</label>
              <textarea
                rows={3}
                disabled={!canEdit || selectedElement.locked}
                value={selectedElement.text ?? ""}
                onChange={(e) => update({ text: e.target.value })}
                className="w-full rounded-lg border border-border-subtle bg-surface px-2.5 py-1.5 text-xs text-text-main focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue"
              />
              <div className="mt-1.5 flex flex-wrap gap-1">
                {DYNAMIC_PLACEHOLDERS.slice(0, 5).map((ph) => (
                  <button
                    key={ph.key}
                    type="button"
                    disabled={!canEdit || selectedElement.locked}
                    onClick={() => update({ text: `${selectedElement.text ?? ""}${ph.token}` })}
                    className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-brand-navy hover:bg-slate-200 disabled:opacity-40"
                  >
                    +{ph.key}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="mb-1 block font-medium text-text-main">Font Family</label>
              <select
                disabled={!canEdit || selectedElement.locked}
                value={selectedElement.fontFamily ?? "Inter"}
                onChange={(e) => update({ fontFamily: e.target.value })}
                className="h-8 w-full rounded-lg border border-border-subtle bg-surface px-2.5 text-xs text-text-main focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue"
              >
                {SUPPORTED_CERTIFICATE_FONTS.map((font) => (
                  <option key={font} value={font}>
                    {font}
                  </option>
                ))}
              </select>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <NumberField
                label="Font Size"
                value={selectedElement.fontSize ?? 16}
                min={6}
                max={200}
                disabled={!canEdit || selectedElement.locked}
                onChange={(v) => update({ fontSize: Math.min(200, Math.max(6, v)) })}
              />
              <NumberField
                label="Line Height"
                value={selectedElement.lineHeight ?? 1.25}
                min={0.8}
                max={3}
                step={0.05}
                disabled={!canEdit || selectedElement.locked}
                onChange={(v) => update({ lineHeight: Math.min(3, Math.max(0.8, v)) })}
              />
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="mb-1 block font-medium text-text-main">Style</label>
                <div className="flex gap-1">
                  <Button
                    type="button"
                    size="sm"
                    variant={selectedElement.fontWeight === "BOLD" ? "primary" : "outline"}
                    disabled={!canEdit || selectedElement.locked}
                    onClick={() =>
                      update({
                        fontWeight: selectedElement.fontWeight === "BOLD" ? "NORMAL" : "BOLD",
                      })
                    }
                    className="h-8 flex-1 text-xs font-bold"
                  >
                    B
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant={selectedElement.fontStyle === "ITALIC" ? "primary" : "outline"}
                    disabled={!canEdit || selectedElement.locked}
                    onClick={() =>
                      update({
                        fontStyle: selectedElement.fontStyle === "ITALIC" ? "NORMAL" : "ITALIC",
                      })
                    }
                    className="h-8 flex-1 text-xs italic"
                  >
                    I
                  </Button>
                </div>
              </div>

              <div>
                <label className="mb-1 block font-medium text-text-main">Align</label>
                <div className="flex gap-1">
                  {(["LEFT", "CENTER", "RIGHT"] as const).map((align) => (
                    <Button
                      key={align}
                      type="button"
                      size="sm"
                      variant={
                        (selectedElement.textAlign ?? "CENTER") === align ? "primary" : "outline"
                      }
                      disabled={!canEdit || selectedElement.locked}
                      onClick={() => update({ textAlign: align })}
                      className="h-8 flex-1 px-1 text-[10px]"
                    >
                      {align[0]}
                    </Button>
                  ))}
                </div>
              </div>
            </div>

            <ColorField
              label="Text Color"
              value={selectedElement.fill || "#0F172A"}
              disabled={!canEdit || selectedElement.locked}
              onChange={(hex) => update({ fill: hex })}
            />
          </div>
        ) : null}

        {selectedElement.type === "SHAPE" ? (
          <div className="space-y-3 border-t border-border-subtle pt-3">
            <p className="text-[11px] font-semibold text-text-secondary uppercase">
              Shape Appearance
            </p>
            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <label className="font-medium text-text-main">Fill Color</label>
                <label className="flex items-center gap-1 text-[11px] text-text-secondary">
                  <input
                    type="checkbox"
                    disabled={!canEdit || selectedElement.locked}
                    checked={
                      (selectedElement.fill ?? "transparent").toLowerCase() === "transparent"
                    }
                    onChange={(e) => update({ fill: e.target.checked ? "transparent" : "#F8FAFC" })}
                  />
                  Transparent
                </label>
              </div>
              {(selectedElement.fill ?? "transparent").toLowerCase() !== "transparent" ? (
                <ColorField
                  label=""
                  value={selectedElement.fill || "#F8FAFC"}
                  disabled={!canEdit || selectedElement.locked}
                  onChange={(hex) => update({ fill: hex })}
                />
              ) : null}
            </div>

            <ColorField
              label="Stroke Color"
              value={selectedElement.stroke || "#142D4E"}
              disabled={!canEdit || selectedElement.locked}
              onChange={(hex) => update({ stroke: hex })}
            />

            <div className="grid grid-cols-2 gap-2">
              <NumberField
                label="Stroke Width"
                value={selectedElement.strokeWidth ?? 1}
                min={0}
                max={40}
                disabled={!canEdit || selectedElement.locked}
                onChange={(v) => update({ strokeWidth: Math.min(40, Math.max(0, v)) })}
              />
              {selectedElement.shapeKind === "RECTANGLE" ? (
                <NumberField
                  label="Corner Radius"
                  value={selectedElement.cornerRadius ?? 0}
                  min={0}
                  max={200}
                  disabled={!canEdit || selectedElement.locked}
                  onChange={(v) => update({ cornerRadius: Math.max(0, v) })}
                />
              ) : null}
            </div>
          </div>
        ) : null}

        {selectedElement.type === "QR" ? (
          <div className="space-y-3 border-t border-border-subtle pt-3">
            <p className="text-[11px] font-semibold text-text-secondary uppercase">
              QR Verification Payload
            </p>
            <div>
              <label className="mb-1 block font-medium text-text-main">Data Template</label>
              <Input
                disabled={!canEdit || selectedElement.locked}
                value={selectedElement.qrDataTemplate ?? "{{verificationUrl}}"}
                onChange={(e) => update({ qrDataTemplate: e.target.value })}
                className="font-mono text-xs"
              />
            </div>
            {(selectedElement.width < 48 || selectedElement.height < 48) && (
              <div className="rounded-md border border-amber-200 bg-amber-50 p-2 text-[11px] text-amber-800">
                Keep QR size at least 48×48 pt so printed certificates scan reliably.
              </div>
            )}
            <ColorField
              label="Foreground"
              value={selectedElement.foregroundColor || "#142D4E"}
              disabled={!canEdit || selectedElement.locked}
              onChange={(hex) => update({ foregroundColor: hex })}
            />
            <ColorField
              label="Background"
              value={selectedElement.backgroundColor || "#FFFFFF"}
              disabled={!canEdit || selectedElement.locked}
              onChange={(hex) => update({ backgroundColor: hex })}
            />
          </div>
        ) : null}

        {selectedElement.type === "IMAGE" ||
        selectedElement.type === "LOGO" ||
        selectedElement.type === "SIGNATURE" ||
        selectedElement.type === "STAMP" ? (
          <div className="space-y-3 border-t border-border-subtle pt-3">
            <p className="text-[11px] font-semibold text-text-secondary uppercase">
              Media Asset Binding
            </p>
            <div>
              <label className="mb-1 block font-medium text-text-main">Organization Asset</label>
              <select
                disabled={!canEdit || selectedElement.locked}
                value={selectedElement.assetId ?? ""}
                onChange={(e) => update({ assetId: e.target.value || null })}
                className="h-8 w-full rounded-lg border border-border-subtle bg-surface px-2 text-xs text-text-main focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue"
              >
                <option value="">-- Unassigned Placeholder --</option>
                {assets.map((asset) => (
                  <option key={asset.id} value={asset.id}>
                    [{asset.assetType}] {asset.originalFileName}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="mb-1 block font-medium text-text-main">Image Fit</label>
              <select
                disabled={!canEdit || selectedElement.locked}
                value={selectedElement.fit ?? "CONTAIN"}
                onChange={(e) => update({ fit: e.target.value })}
                className="h-8 w-full rounded-lg border border-border-subtle bg-surface px-2 text-xs text-text-main focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue"
              >
                <option value="CONTAIN">Contain (Preserve aspect ratio)</option>
                <option value="COVER">Cover (Fill frame)</option>
              </select>
            </div>
          </div>
        ) : null}
      </div>
    </aside>
  );
}

function NumberField({
  label,
  value,
  min,
  max,
  step = 1,
  disabled,
  onChange,
}: {
  label: string;
  value: number;
  min?: number;
  max?: number;
  step?: number;
  disabled?: boolean;
  onChange: (value: number) => void;
}) {
  return (
    <div>
      <label className="mb-1 block text-[11px] font-medium text-text-secondary">{label}</label>
      <Input
        type="number"
        min={min}
        max={max}
        step={step}
        disabled={disabled}
        value={Number.isFinite(value) ? value : 0}
        onChange={(e) => {
          const parsed = Number(e.target.value);
          if (!Number.isNaN(parsed)) {
            onChange(parsed);
          }
        }}
        className="h-7 text-xs"
      />
    </div>
  );
}

function ColorField({
  label,
  value,
  disabled,
  onChange,
}: {
  label: string;
  value: string;
  disabled?: boolean;
  onChange: (hex: string) => void;
}) {
  const safeColor = normalizeHexColor(value, "#142D4E");
  return (
    <div>
      {label ? <label className="mb-1 block font-medium text-text-main">{label}</label> : null}
      <div className="flex items-center gap-2">
        <input
          type="color"
          disabled={disabled}
          value={safeColor}
          onChange={(e) => onChange(e.target.value.toUpperCase())}
          className="h-8 w-10 cursor-pointer rounded border border-border-subtle bg-surface p-0.5"
        />
        <Input
          disabled={disabled}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className="h-8 font-mono text-xs"
        />
      </div>
    </div>
  );
}

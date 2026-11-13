"use client";

import {
  AlignCenter,
  AlignCenterHorizontal,
  AlignCenterVertical,
  AlignLeft,
  AlignRight,
  ArrowDown,
  ArrowUp,
  Bold,
  Copy,
  Italic,
  Trash2,
} from "lucide-react";
import type { DesignElement, DesignPage } from "@/lib/api/types";
import {
  SUPPORTED_CERTIFICATE_FONTS,
  normalizeHexColor,
  type PageAlignment,
} from "@/lib/editor/design-helpers";

interface ContextualFormatBarProps {
  canEdit: boolean;
  page: DesignPage;
  selectedElement: DesignElement | null;
  onUpdateElement: (elementId: string, patch: Partial<DesignElement>) => void;
  onAlignElement: (elementId: string, alignment: PageAlignment) => void;
  onReorderLayer: (elementId: string, direction: "up" | "down") => void;
  onDuplicateElement: (elementId: string) => void;
  onDeleteElement: (elementId: string) => void;
}

const FONT_SIZE_PRESETS = [9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 32, 36, 42] as const;

export function ContextualFormatBar({
  canEdit,
  page,
  selectedElement,
  onUpdateElement,
  onAlignElement,
  onReorderLayer,
  onDuplicateElement,
  onDeleteElement,
}: ContextualFormatBarProps) {
  if (!selectedElement) {
    return (
      <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-border-subtle bg-surface px-3.5 py-2 text-xs shadow-2xs">
        <div className="flex items-center gap-2 text-text-secondary">
          <span className="inline-flex items-center gap-1.5 font-semibold text-text-main">
            A4 — {page.orientation}
          </span>
          <span>·</span>
          <span>
            {page.width} × {page.height} {page.unit}
          </span>
          <span>·</span>
          <span className="inline-flex items-center gap-1.5">
            <span
              className="inline-block h-3.5 w-3.5 rounded border border-border-subtle"
              style={{ backgroundColor: normalizeHexColor(page.backgroundColor, "#FFFFFF") }}
            />
            {normalizeHexColor(page.backgroundColor, "#FFFFFF")}
          </span>
        </div>
        <span className="text-[11px] text-text-secondary">
          Click any element on the canvas to format typography, color, alignment, or layers
        </span>
      </div>
    );
  }

  const disabled = !canEdit || selectedElement.locked;
  const currentColor =
    selectedElement.type === "QR"
      ? normalizeHexColor(selectedElement.foregroundColor, "#142D4E")
      : normalizeHexColor(selectedElement.fill, "#0F172A");

  return (
    <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-border-subtle bg-surface px-3 py-1.5 text-xs shadow-2xs">
      <div className="flex flex-wrap items-center gap-1.5">
        {/* Color Swatch */}
        <label
          title="Color"
          className="inline-flex h-7 cursor-pointer items-center gap-1.5 rounded-md border border-border-subtle px-1.5 hover:bg-slate-50"
        >
          <input
            type="color"
            disabled={disabled}
            aria-label="Selected element color"
            value={currentColor}
            onChange={(e) => {
              const hex = e.target.value.toUpperCase();
              if (selectedElement.type === "QR") {
                onUpdateElement(selectedElement.id, { foregroundColor: hex });
              } else {
                onUpdateElement(selectedElement.id, { fill: hex });
              }
            }}
            className="h-4 w-5 cursor-pointer border-0 bg-transparent p-0"
          />
          <span className="hidden font-mono text-[11px] text-text-secondary sm:inline">
            {currentColor}
          </span>
        </label>

        {selectedElement.type === "TEXT" ? (
          <>
            <select
              aria-label="Font family"
              disabled={disabled}
              value={selectedElement.fontFamily || "Inter"}
              onChange={(e) => onUpdateElement(selectedElement.id, { fontFamily: e.target.value })}
              className="h-7 rounded-md border border-border-subtle bg-surface px-2 text-xs text-text-main focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue"
            >
              {SUPPORTED_CERTIFICATE_FONTS.map((font) => (
                <option key={font} value={font}>
                  {font}
                </option>
              ))}
            </select>

            <select
              aria-label="Font size"
              disabled={disabled}
              value={selectedElement.fontSize ?? 18}
              onChange={(e) =>
                onUpdateElement(selectedElement.id, {
                  fontSize: Number(e.target.value) || 18,
                })
              }
              className="h-7 rounded-md border border-border-subtle bg-surface px-1.5 text-xs text-text-main focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue"
            >
              {FONT_SIZE_PRESETS.map((size) => (
                <option key={size} value={size}>
                  {size}pt
                </option>
              ))}
              {!FONT_SIZE_PRESETS.includes(
                (selectedElement.fontSize ?? 18) as (typeof FONT_SIZE_PRESETS)[number]
              ) ? (
                <option value={selectedElement.fontSize ?? 18}>{selectedElement.fontSize}pt</option>
              ) : null}
            </select>

            <div className="flex items-center rounded-md border border-border-subtle p-0.5">
              <button
                type="button"
                title="Bold"
                disabled={disabled}
                onClick={() =>
                  onUpdateElement(selectedElement.id, {
                    fontWeight: selectedElement.fontWeight === "BOLD" ? "NORMAL" : "BOLD",
                  })
                }
                className={`rounded p-1 transition-colors ${
                  selectedElement.fontWeight === "BOLD"
                    ? "bg-brand-navy text-white"
                    : "text-text-secondary hover:bg-slate-100"
                }`}
              >
                <Bold className="h-3.5 w-3.5" />
              </button>
              <button
                type="button"
                title="Italic"
                disabled={disabled}
                onClick={() =>
                  onUpdateElement(selectedElement.id, {
                    fontStyle: selectedElement.fontStyle === "ITALIC" ? "NORMAL" : "ITALIC",
                  })
                }
                className={`rounded p-1 transition-colors ${
                  selectedElement.fontStyle === "ITALIC"
                    ? "bg-brand-navy text-white"
                    : "text-text-secondary hover:bg-slate-100"
                }`}
              >
                <Italic className="h-3.5 w-3.5" />
              </button>
            </div>

            <div className="flex items-center rounded-md border border-border-subtle p-0.5">
              {(
                [
                  { value: "LEFT", icon: AlignLeft, title: "Align text left" },
                  { value: "CENTER", icon: AlignCenter, title: "Align text center" },
                  { value: "RIGHT", icon: AlignRight, title: "Align text right" },
                ] as const
              ).map((opt) => {
                const Icon = opt.icon;
                const active = (selectedElement.textAlign ?? "CENTER") === opt.value;
                return (
                  <button
                    key={opt.value}
                    type="button"
                    title={opt.title}
                    disabled={disabled}
                    onClick={() => onUpdateElement(selectedElement.id, { textAlign: opt.value })}
                    className={`rounded p-1 transition-colors ${
                      active
                        ? "bg-brand-blue/15 text-brand-blue"
                        : "text-text-secondary hover:bg-slate-100"
                    }`}
                  >
                    <Icon className="h-3.5 w-3.5" />
                  </button>
                );
              })}
            </div>
          </>
        ) : (
          <span className="rounded-md bg-slate-100 px-2 py-1 text-[11px] font-medium text-text-main">
            {selectedElement.name || selectedElement.type}
          </span>
        )}
      </div>

      <div className="flex items-center gap-1">
        <button
          type="button"
          title="Center horizontally on page"
          disabled={disabled}
          onClick={() => onAlignElement(selectedElement.id, "center-x")}
          className="rounded-md border border-border-subtle p-1.5 text-text-secondary hover:bg-slate-50 disabled:opacity-40"
        >
          <AlignCenterHorizontal className="h-3.5 w-3.5" />
        </button>
        <button
          type="button"
          title="Center vertically on page"
          disabled={disabled}
          onClick={() => onAlignElement(selectedElement.id, "center-y")}
          className="rounded-md border border-border-subtle p-1.5 text-text-secondary hover:bg-slate-50 disabled:opacity-40"
        >
          <AlignCenterVertical className="h-3.5 w-3.5" />
        </button>
        <button
          type="button"
          title="Bring forward"
          disabled={!canEdit}
          onClick={() => onReorderLayer(selectedElement.id, "up")}
          className="rounded-md border border-border-subtle p-1.5 text-text-secondary hover:bg-slate-50 disabled:opacity-40"
        >
          <ArrowUp className="h-3.5 w-3.5" />
        </button>
        <button
          type="button"
          title="Send backward"
          disabled={!canEdit}
          onClick={() => onReorderLayer(selectedElement.id, "down")}
          className="rounded-md border border-border-subtle p-1.5 text-text-secondary hover:bg-slate-50 disabled:opacity-40"
        >
          <ArrowDown className="h-3.5 w-3.5" />
        </button>
        <button
          type="button"
          title="Duplicate element"
          disabled={!canEdit}
          onClick={() => onDuplicateElement(selectedElement.id)}
          className="rounded-md border border-border-subtle p-1.5 text-text-secondary hover:bg-slate-50 disabled:opacity-40"
        >
          <Copy className="h-3.5 w-3.5" />
        </button>
        <button
          type="button"
          title="Delete element"
          disabled={!canEdit}
          onClick={() => onDeleteElement(selectedElement.id)}
          className="rounded-md border border-rose-200 p-1.5 text-rose-600 hover:bg-rose-50 disabled:opacity-40"
        >
          <Trash2 className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}

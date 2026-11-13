"use client";

import * as React from "react";
import { QrCode, Image as ImageIcon } from "lucide-react";
import { resolveBackendAssetUrl } from "@/lib/api/client";
import type { DesignDocument, DesignElement } from "@/lib/api/types";
import { cn } from "@/lib/utils";

interface CertificatePreviewCanvasProps {
  design: DesignDocument | null | undefined;
  assetUrls?: Record<string, string>;
  resolvedPlaceholders?: Record<string, string>;
  className?: string;
}

const FONT_FAMILY_MAP: Record<string, string> = {
  Inter: "var(--font-inter), ui-sans-serif, system-ui, sans-serif",
  "Noto Sans Khmer": "var(--font-noto-khmer), var(--font-inter), sans-serif",
  Georgia: "Georgia, serif",
  "Times New Roman": "'Times New Roman', Times, serif",
  "Courier New": "'Courier New', Courier, monospace",
};

function resolvePlaceholders(
  rawText: string | null | undefined,
  values?: Record<string, string>
): string {
  if (!rawText) return "";
  if (!values) return rawText;
  return rawText.replace(/\{\{([a-zA-Z0-9_]+)\}\}/g, (full, key: string) =>
    key in values ? values[key] : full
  );
}

export function CertificatePreviewCanvas({
  design,
  assetUrls = {},
  resolvedPlaceholders,
  className,
}: CertificatePreviewCanvasProps) {
  const containerRef = React.useRef<HTMLDivElement | null>(null);
  const pageWidth = design?.page?.width && design.page.width > 0 ? design.page.width : 842;
  const pageHeight = design?.page?.height && design.page.height > 0 ? design.page.height : 595;
  const [scale, setScale] = React.useState(0.5);

  React.useEffect(() => {
    const node = containerRef.current;
    if (!node) return;

    const updateScale = (width: number) => {
      if (width > 0) {
        setScale(width / pageWidth);
      }
    };

    const observer = new ResizeObserver((entries) => {
      const entry = entries[0];
      if (entry) {
        updateScale(entry.contentRect.width);
      }
    });
    observer.observe(node);
    return () => observer.disconnect();
  }, [pageWidth]);

  if (!design || !design.page) {
    return (
      <div
        className={cn(
          "flex aspect-[842/595] w-full items-center justify-center rounded-lg border border-border-subtle bg-slate-50 text-xs text-text-secondary",
          className
        )}
      >
        No preview data available
      </div>
    );
  }

  const backgroundAssetUrl = design.page.backgroundAssetId
    ? resolveBackendAssetUrl(assetUrls[design.page.backgroundAssetId])
    : null;

  const sortedElements = [...(design.elements ?? [])]
    .filter((el) => el.visible !== false)
    .sort((a, b) => a.zIndex - b.zIndex);

  return (
    <div
      ref={containerRef}
      style={{
        aspectRatio: `${pageWidth} / ${pageHeight}`,
        backgroundColor: design.page.backgroundColor || "#FFFFFF",
      }}
      className={cn(
        "relative w-full overflow-hidden rounded-lg border border-border-subtle shadow-xs select-none",
        className
      )}
    >
      {backgroundAssetUrl ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={backgroundAssetUrl}
          alt="Certificate background"
          className="pointer-events-none absolute inset-0 h-full w-full object-cover"
        />
      ) : null}

      {sortedElements.map((element) => (
        <CanvasElement
          key={element.id}
          element={element}
          pageWidth={pageWidth}
          pageHeight={pageHeight}
          scale={scale}
          assetUrls={assetUrls}
          resolvedPlaceholders={resolvedPlaceholders}
        />
      ))}
    </div>
  );
}

function CanvasElement({
  element,
  pageWidth,
  pageHeight,
  scale,
  assetUrls,
  resolvedPlaceholders,
}: {
  element: DesignElement;
  pageWidth: number;
  pageHeight: number;
  scale: number;
  assetUrls: Record<string, string>;
  resolvedPlaceholders?: Record<string, string>;
}) {
  const leftPct = (element.x / pageWidth) * 100;
  const topPct = (element.y / pageHeight) * 100;
  const widthPct = (element.width / pageWidth) * 100;
  const heightPct = (element.height / pageHeight) * 100;

  const baseStyle: React.CSSProperties = {
    position: "absolute",
    left: `${leftPct}%`,
    top: `${topPct}%`,
    width: `${widthPct}%`,
    height: `${heightPct}%`,
    opacity: element.opacity ?? 1,
    transform: element.rotation ? `rotate(${element.rotation}deg)` : undefined,
    transformOrigin: "center center",
  };

  if (element.type === "SHAPE") {
    const strokeWidth = Math.max(0.75, (element.strokeWidth ?? 0) * scale);
    const fill =
      !element.fill || element.fill.toLowerCase() === "transparent" ? "transparent" : element.fill;

    if (element.shapeKind === "LINE") {
      return (
        <div
          style={{
            ...baseStyle,
            borderTop: `${Math.max(1, strokeWidth)}px solid ${element.stroke || element.fill || "#142D4E"}`,
            height: 0,
          }}
        />
      );
    }

    return (
      <div
        style={{
          ...baseStyle,
          backgroundColor: fill,
          border: element.stroke ? `${strokeWidth}px solid ${element.stroke}` : undefined,
          borderRadius:
            element.shapeKind === "CIRCLE"
              ? "9999px"
              : element.cornerRadius
                ? `${element.cornerRadius * scale}px`
                : undefined,
        }}
      />
    );
  }

  if (element.type === "TEXT") {
    const fontFamily = FONT_FAMILY_MAP[element.fontFamily ?? "Inter"] ?? FONT_FAMILY_MAP.Inter;
    const fontSizePx = Math.max(5, (element.fontSize ?? 14) * scale);
    const textAlign =
      element.textAlign === "LEFT" ? "left" : element.textAlign === "RIGHT" ? "right" : "center";

    return (
      <div
        style={{
          ...baseStyle,
          fontFamily,
          fontSize: `${fontSizePx}px`,
          fontWeight: element.fontWeight === "BOLD" ? 700 : 400,
          fontStyle: element.fontStyle === "ITALIC" ? "italic" : "normal",
          color: element.fill || "#0F172A",
          textAlign,
          lineHeight: element.lineHeight ?? 1.25,
          display: "flex",
          alignItems: "center",
          justifyContent:
            textAlign === "left" ? "flex-start" : textAlign === "right" ? "flex-end" : "center",
          whiteSpace: "pre-wrap",
          wordBreak: "break-word",
          overflow: "hidden",
        }}
      >
        <span className="w-full">{resolvePlaceholders(element.text, resolvedPlaceholders)}</span>
      </div>
    );
  }

  if (element.type === "QR") {
    return (
      <div
        style={{
          ...baseStyle,
          backgroundColor: element.backgroundColor || "#FFFFFF",
          color: element.foregroundColor || "#142D4E",
        }}
        className="flex flex-col items-center justify-center rounded-xs border border-border-subtle p-1"
        title={resolvePlaceholders(element.qrDataTemplate, resolvedPlaceholders)}
      >
        <QrCode className="h-[72%] w-[72%]" strokeWidth={1.75} />
      </div>
    );
  }

  const assetUrl = element.assetId ? resolveBackendAssetUrl(assetUrls[element.assetId]) : null;
  const objectFitClass = element.fit === "COVER" ? "object-cover" : "object-contain";

  return (
    <div style={baseStyle} className="flex items-center justify-center overflow-hidden">
      {assetUrl ? (
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={assetUrl}
          alt={element.name || element.type}
          className={cn("h-full w-full", objectFitClass)}
        />
      ) : (
        <div className="flex h-full w-full flex-col items-center justify-center rounded-xs border border-dashed border-slate-300 bg-slate-50/70 text-[9px] text-slate-500">
          <ImageIcon className="h-3.5 w-3.5 opacity-70" />
          <span className="mt-0.5 truncate px-1 uppercase tracking-wide">{element.type}</span>
        </div>
      )}
    </div>
  );
}

import type { DesignDocument, DesignElement, DesignElementType, DesignPage } from "@/lib/api/types";

export const SUPPORTED_CERTIFICATE_FONTS = [
  "Inter",
  "Noto Sans Khmer",
  "Georgia",
  "Times New Roman",
  "Courier New",
] as const;

export const DYNAMIC_PLACEHOLDERS = [
  { key: "recipientName", label: "Recipient Name", token: "{{recipientName}}" },
  { key: "courseName", label: "Course / Program", token: "{{courseName}}" },
  { key: "organizationName", label: "Organization Name", token: "{{organizationName}}" },
  { key: "issuedDate", label: "Issued Date", token: "{{issuedDate}}" },
  { key: "certificateNumber", label: "Certificate Number", token: "{{certificateNumber}}" },
  { key: "verificationUrl", label: "Verification URL", token: "{{verificationUrl}}" },
  { key: "issuerName", label: "Authorized Signatory", token: "{{issuerName}}" },
] as const;

export function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

export function normalizeHexColor(value: string | null | undefined, fallback: string): string {
  if (!value) return fallback;
  const trimmed = value.trim();
  if (/^#[0-9a-fA-F]{6}$/.test(trimmed)) {
    return trimmed.toUpperCase();
  }
  return fallback;
}

export function normalizeAngle(angle: number): number {
  const normalized = ((angle % 360) + 360) % 360;
  return round2(normalized);
}

export function generateElementId(prefix: string): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `${prefix}-${crypto.randomUUID().slice(0, 8)}`;
  }
  return `${prefix}-${Math.random().toString(36).slice(2, 10)}`;
}

export function normalizeLayerOrder(elements: DesignElement[]): DesignElement[] {
  const sorted = [...elements].sort((a, b) => a.zIndex - b.zIndex);
  return sorted.map((el, index) => ({
    ...el,
    zIndex: index,
  }));
}

export function reorderElementLayer(
  elements: DesignElement[],
  elementId: string,
  direction: "up" | "down" | "front" | "back"
): DesignElement[] {
  const normalized = normalizeLayerOrder(elements);
  const currentIndex = normalized.findIndex((el) => el.id === elementId);
  if (currentIndex === -1) return normalized;

  const target = normalized[currentIndex];
  const rest = normalized.filter((el) => el.id !== elementId);
  let nextIndex = currentIndex;

  if (direction === "up") {
    nextIndex = Math.min(normalized.length - 1, currentIndex + 1);
  } else if (direction === "down") {
    nextIndex = Math.max(0, currentIndex - 1);
  } else if (direction === "front") {
    nextIndex = normalized.length - 1;
  } else if (direction === "back") {
    nextIndex = 0;
  }

  rest.splice(nextIndex, 0, target);
  return rest.map((el, index) => ({
    ...el,
    zIndex: index,
  }));
}

export type PageAlignment = "left" | "center-x" | "right" | "top" | "center-y" | "bottom";

export function alignElementToPage(
  element: DesignElement,
  page: DesignPage,
  alignment: PageAlignment
): DesignElement {
  const safeMargin = 36;
  switch (alignment) {
    case "left":
      return { ...element, x: safeMargin };
    case "center-x":
      return { ...element, x: round2((page.width - element.width) / 2) };
    case "right":
      return { ...element, x: round2(page.width - element.width - safeMargin) };
    case "top":
      return { ...element, y: safeMargin };
    case "center-y":
      return { ...element, y: round2((page.height - element.height) / 2) };
    case "bottom":
      return { ...element, y: round2(page.height - element.height - safeMargin) };
  }
}

export interface CreateElementOptions {
  text?: string;
  name?: string;
  fontFamily?: string;
  fontSize?: number;
  fontWeight?: "NORMAL" | "BOLD";
  shapeKind?: "RECTANGLE" | "CIRCLE" | "LINE";
  assetId?: string;
  width?: number;
  height?: number;
}

export function createDefaultElement(
  type: DesignElementType,
  page: DesignPage,
  nextZIndex: number,
  options: CreateElementOptions = {}
): DesignElement {
  const centerX = round2(page.width / 2);
  const centerY = round2(page.height / 2);

  if (type === "TEXT") {
    const width = options.width ?? Math.min(420, round2(page.width * 0.6));
    const height = options.height ?? 44;
    return {
      id: generateElementId("txt"),
      type: "TEXT",
      name: options.name ?? "Text Block",
      x: round2(centerX - width / 2),
      y: round2(centerY - height / 2),
      width,
      height,
      rotation: 0,
      opacity: 1,
      visible: true,
      locked: false,
      zIndex: nextZIndex,
      text: options.text ?? "Sample Certificate Text",
      fontFamily: options.fontFamily ?? "Inter",
      fontSize: options.fontSize ?? 18,
      fontWeight: options.fontWeight ?? "NORMAL",
      fontStyle: "NORMAL",
      fill: "#0F172A",
      textAlign: "CENTER",
      lineHeight: 1.3,
    };
  }

  if (type === "QR") {
    const size = 84;
    return {
      id: generateElementId("qr"),
      type: "QR",
      name: options.name ?? "Verification QR",
      x: round2(page.width - size - 56),
      y: round2(page.height - size - 56),
      width: size,
      height: size,
      rotation: 0,
      opacity: 1,
      visible: true,
      locked: false,
      zIndex: nextZIndex,
      qrDataTemplate: "{{verificationUrl}}",
      foregroundColor: "#142D4E",
      backgroundColor: "#FFFFFF",
    };
  }

  if (type === "SHAPE") {
    const shapeKind = options.shapeKind ?? "RECTANGLE";
    if (shapeKind === "LINE") {
      const width = 260;
      return {
        id: generateElementId("line"),
        type: "SHAPE",
        name: options.name ?? "Divider Line",
        x: round2(centerX - width / 2),
        y: centerY,
        width,
        height: 4,
        rotation: 0,
        opacity: 1,
        visible: true,
        locked: false,
        zIndex: nextZIndex,
        fill: "#C6A45B",
        shapeKind: "LINE",
        stroke: "#C6A45B",
        strokeWidth: 2,
        cornerRadius: 0,
      };
    }

    const width = shapeKind === "CIRCLE" ? 110 : 220;
    const height = shapeKind === "CIRCLE" ? 110 : 120;
    return {
      id: generateElementId("shp"),
      type: "SHAPE",
      name: options.name ?? (shapeKind === "CIRCLE" ? "Circle Accent" : "Border Frame"),
      x: round2(centerX - width / 2),
      y: round2(centerY - height / 2),
      width,
      height,
      rotation: 0,
      opacity: 1,
      visible: true,
      locked: false,
      zIndex: nextZIndex,
      fill: "transparent",
      shapeKind,
      stroke: "#142D4E",
      strokeWidth: 2,
      cornerRadius: 0,
    };
  }

  // IMAGE / LOGO / SIGNATURE / STAMP
  const defaultWidth = type === "SIGNATURE" ? 160 : 110;
  const defaultHeight = type === "SIGNATURE" ? 64 : 110;
  const width = options.width ? Math.min(260, Math.max(40, options.width)) : defaultWidth;
  const height = options.height ? Math.min(180, Math.max(40, options.height)) : defaultHeight;

  return {
    id: generateElementId(type.toLowerCase()),
    type,
    name: options.name ?? `${type.charAt(0)}${type.slice(1).toLowerCase()}`,
    x: round2(centerX - width / 2),
    y: round2(centerY - height / 2),
    width,
    height,
    rotation: 0,
    opacity: 1,
    visible: true,
    locked: false,
    zIndex: nextZIndex,
    assetId: options.assetId ?? null,
    fit: "CONTAIN",
  };
}

export function duplicateElement(element: DesignElement, nextZIndex: number): DesignElement {
  return {
    ...element,
    id: generateElementId(element.type.toLowerCase()),
    name: `${element.name || element.type} Copy`,
    x: round2(element.x + 18),
    y: round2(element.y + 18),
    locked: false,
    zIndex: nextZIndex,
  };
}

function normalizeOptionalHexColor(value: string | null | undefined): string | undefined {
  if (!value) return undefined;
  const trimmed = value.trim();
  return /^#[0-9a-fA-F]{6}$/.test(trimmed) ? trimmed.toUpperCase() : undefined;
}

export function sanitizeDesignForValidation(doc: DesignDocument): DesignDocument {
  const ordered = normalizeLayerOrder(doc.elements).map((el) => {
    const base: DesignElement = {
      ...el,
      x: round2(el.x),
      y: round2(el.y),
      width: Math.max(1, round2(el.width)),
      height: Math.max(1, round2(el.height)),
      rotation: normalizeAngle(el.rotation ?? 0),
      opacity: Math.min(1, Math.max(0, round2(el.opacity ?? 1))),
    };

    if (base.type === "SHAPE") {
      const normalizedStroke = normalizeOptionalHexColor(base.stroke);
      const fillTrimmed = base.fill?.trim();
      const normalizedFill =
        fillTrimmed && fillTrimmed.toLowerCase() === "transparent"
          ? "transparent"
          : normalizeHexColor(fillTrimmed, "#F8FAFC");
      return {
        ...base,
        fill: normalizedFill,
        stroke: normalizedStroke,
        strokeWidth:
          base.strokeWidth !== undefined && base.strokeWidth !== null
            ? Math.min(40, Math.max(0, round2(base.strokeWidth)))
            : undefined,
      };
    }

    if (base.type === "TEXT") {
      return {
        ...base,
        fill: normalizeHexColor(base.fill, "#0F172A"),
        fontSize: Math.min(200, Math.max(6, round2(base.fontSize ?? 16))),
        lineHeight:
          base.lineHeight !== undefined && base.lineHeight !== null
            ? Math.min(3, Math.max(0.8, round2(base.lineHeight)))
            : 1.25,
      };
    }

    if (base.type === "QR") {
      return {
        ...base,
        foregroundColor: normalizeHexColor(base.foregroundColor, "#142D4E"),
        backgroundColor: normalizeHexColor(base.backgroundColor, "#FFFFFF"),
      };
    }

    return base;
  });

  return {
    schemaVersion: "1.0",
    page: {
      ...doc.page,
      width: round2(doc.page.width),
      height: round2(doc.page.height),
      backgroundColor: normalizeHexColor(doc.page.backgroundColor, "#FFFFFF"),
    },
    elements: ordered,
  };
}

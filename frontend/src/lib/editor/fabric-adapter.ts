import {
  Canvas,
  Circle,
  FabricImage,
  FabricObject,
  FabricText,
  Group,
  Line,
  Rect,
  Textbox,
} from "fabric";
import { resolveBackendAssetUrl } from "@/lib/api/client";
import type { DesignDocument, DesignElement, DesignElementType } from "@/lib/api/types";
import { normalizeAngle, round2 } from "./design-helpers";

export interface StudioObjectMeta {
  elementId: string;
  elementType: DesignElementType;
}

const objectMetaMap = new WeakMap<FabricObject, StudioObjectMeta>();

export function setStudioObjectMeta(obj: FabricObject, meta: StudioObjectMeta): void {
  objectMetaMap.set(obj, meta);
}

export function getStudioObjectMeta(obj: FabricObject | null | undefined): StudioObjectMeta | null {
  if (!obj) return null;
  return objectMetaMap.get(obj) ?? null;
}

export function resolveCanvasFontFamily(fontFamily: string | null | undefined): string {
  const family = fontFamily ?? "Inter";
  if (typeof window === "undefined") {
    return family;
  }
  const styles = window.getComputedStyle(document.documentElement);
  if (family === "Inter") {
    const cssVar = styles.getPropertyValue("--font-inter").trim();
    return cssVar || "Inter, sans-serif";
  }
  if (family === "Noto Sans Khmer") {
    const cssVar = styles.getPropertyValue("--font-noto-khmer").trim();
    return cssVar ? `${cssVar}, "Noto Sans Khmer", sans-serif` : '"Noto Sans Khmer", sans-serif';
  }
  return family;
}

function buildPlaceholderBadge(element: DesignElement, label: string, subLabel?: string): Group {
  const frame = new Rect({
    originX: "left",
    originY: "top",
    left: 0,
    top: 0,
    width: element.width,
    height: element.height,
    fill: element.type === "QR" ? element.backgroundColor || "#FFFFFF" : "#F8FAFC",
    stroke: element.type === "QR" ? element.foregroundColor || "#142D4E" : "#94A3B8",
    strokeWidth: element.type === "QR" ? 2 : 1.5,
    strokeDashArray: element.type === "QR" ? undefined : [5, 4],
    rx: 4,
    ry: 4,
  });

  const title = new FabricText(label, {
    originX: "center",
    originY: "center",
    left: element.width / 2,
    top: subLabel ? element.height / 2 - 7 : element.height / 2,
    fontSize: Math.max(10, Math.min(14, element.height * 0.22)),
    fontWeight: "bold",
    fontFamily: resolveCanvasFontFamily("Inter"),
    fill: element.type === "QR" ? element.foregroundColor || "#142D4E" : "#334155",
  });

  const children: FabricObject[] = [frame, title];

  if (subLabel) {
    children.push(
      new FabricText(subLabel, {
        originX: "center",
        originY: "center",
        left: element.width / 2,
        top: element.height / 2 + 10,
        fontSize: Math.max(8, Math.min(11, element.height * 0.16)),
        fontFamily: resolveCanvasFontFamily("Inter"),
        fill: "#64748B",
      })
    );
  }

  return new Group(children, {
    originX: "left",
    originY: "top",
    left: element.x,
    top: element.y,
    width: element.width,
    height: element.height,
    angle: element.rotation ?? 0,
    opacity: element.opacity ?? 1,
    visible: element.visible !== false,
  });
}

function createShapeObject(element: DesignElement): FabricObject {
  const fill =
    !element.fill || element.fill.toLowerCase() === "transparent" ? "rgba(0,0,0,0)" : element.fill;
  const stroke = element.stroke || undefined;
  const strokeWidth = element.strokeWidth ?? 0;

  if (element.shapeKind === "CIRCLE") {
    const rx = element.width / 2;
    const ry = element.height / 2;
    return new Circle({
      originX: "left",
      originY: "top",
      left: element.x,
      top: element.y,
      radius: Math.min(rx, ry),
      scaleX: rx / Math.max(1, Math.min(rx, ry)),
      scaleY: ry / Math.max(1, Math.min(rx, ry)),
      fill,
      stroke,
      strokeWidth,
      angle: element.rotation ?? 0,
      opacity: element.opacity ?? 1,
      visible: element.visible !== false,
    });
  }

  if (element.shapeKind === "LINE") {
    return new Line([0, 0, element.width, 0], {
      originX: "left",
      originY: "top",
      left: element.x,
      top: element.y,
      stroke: stroke || (fill === "rgba(0,0,0,0)" ? "#142D4E" : fill),
      strokeWidth: Math.max(1, strokeWidth || element.height),
      angle: element.rotation ?? 0,
      opacity: element.opacity ?? 1,
      visible: element.visible !== false,
    });
  }

  return new Rect({
    originX: "left",
    originY: "top",
    left: element.x,
    top: element.y,
    width: element.width,
    height: element.height,
    fill,
    stroke,
    strokeWidth,
    rx: element.cornerRadius ?? 0,
    ry: element.cornerRadius ?? 0,
    angle: element.rotation ?? 0,
    opacity: element.opacity ?? 1,
    visible: element.visible !== false,
  });
}

function createTextObject(element: DesignElement, editable: boolean): Textbox {
  const align =
    element.textAlign === "LEFT" ? "left" : element.textAlign === "RIGHT" ? "right" : "center";

  return new Textbox(element.text ?? "", {
    originX: "left",
    originY: "top",
    left: element.x,
    top: element.y,
    width: Math.max(24, element.width),
    fontFamily: resolveCanvasFontFamily(element.fontFamily),
    fontSize: element.fontSize ?? 16,
    fontWeight: element.fontWeight === "BOLD" ? "bold" : "normal",
    fontStyle: element.fontStyle === "ITALIC" ? "italic" : "normal",
    fill: element.fill || "#0F172A",
    textAlign: align,
    lineHeight: element.lineHeight ?? 1.25,
    angle: element.rotation ?? 0,
    opacity: element.opacity ?? 1,
    visible: element.visible !== false,
    editable: editable && !element.locked,
    splitByGrapheme: false,
  });
}

async function createFabricObjectForElement(
  element: DesignElement,
  assetUrls: Record<string, string>,
  editable: boolean,
  signal?: AbortSignal
): Promise<FabricObject> {
  let obj: FabricObject;

  if (element.type === "TEXT") {
    obj = createTextObject(element, editable);
  } else if (element.type === "SHAPE") {
    obj = createShapeObject(element);
  } else if (element.type === "QR") {
    obj = buildPlaceholderBadge(element, "QR CODE", "Verify Token");
  } else {
    const signedUrl = element.assetId ? resolveBackendAssetUrl(assetUrls[element.assetId]) : null;
    if (signedUrl) {
      try {
        const img = await FabricImage.fromURL(signedUrl, {
          crossOrigin: "anonymous",
          signal,
        });
        const naturalWidth = Math.max(1, img.width || element.width);
        const naturalHeight = Math.max(1, img.height || element.height);
        img.set({
          originX: "left",
          originY: "top",
          left: element.x,
          top: element.y,
          scaleX: element.width / naturalWidth,
          scaleY: element.height / naturalHeight,
          angle: element.rotation ?? 0,
          opacity: element.opacity ?? 1,
          visible: element.visible !== false,
        });
        obj = img;
      } catch {
        obj = buildPlaceholderBadge(element, element.type, "Asset Preview");
      }
    } else {
      obj = buildPlaceholderBadge(element, element.type, "Pick Asset");
    }
  }

  const locked = Boolean(element.locked) || !editable;
  obj.set({
    selectable: editable && !element.locked,
    evented: editable && !element.locked,
    hasControls: editable && !element.locked,
    lockMovementX: locked,
    lockMovementY: locked,
    lockRotation: locked,
    lockScalingX: locked,
    lockScalingY: locked,
    transparentCorners: false,
    cornerColor: "#2563EB",
    cornerStrokeColor: "#FFFFFF",
    borderColor: "#2563EB",
    cornerSize: 8,
  });

  setStudioObjectMeta(obj, {
    elementId: element.id,
    elementType: element.type,
  });

  return obj;
}

export async function renderDesignToFabricCanvas(params: {
  canvas: Canvas;
  design: DesignDocument;
  assetUrls: Record<string, string>;
  editable: boolean;
  selectedElementId: string | null;
  zoom?: number;
  signal?: AbortSignal;
}): Promise<void> {
  const { canvas, design, assetUrls, editable, selectedElementId, zoom = 1, signal } = params;

  const pageWidth = Math.max(100, design.page.width);
  const pageHeight = Math.max(100, design.page.height);
  const bgAssetUrl = design.page.backgroundAssetId
    ? resolveBackendAssetUrl(assetUrls[design.page.backgroundAssetId])
    : null;

  let loadedBgImage: FabricImage | undefined;
  if (bgAssetUrl) {
    try {
      const bgImg = await FabricImage.fromURL(bgAssetUrl, {
        crossOrigin: "anonymous",
        signal,
      });
      bgImg.set({
        originX: "left",
        originY: "top",
        left: 0,
        top: 0,
        scaleX: pageWidth / Math.max(1, bgImg.width || pageWidth),
        scaleY: pageHeight / Math.max(1, bgImg.height || pageHeight),
      });
      loadedBgImage = bgImg;
    } catch {
      loadedBgImage = undefined;
    }
  }

  const sorted = [...design.elements].sort((a, b) => a.zIndex - b.zIndex);
  const objects = await Promise.all(
    sorted.map((el) => createFabricObjectForElement(el, assetUrls, editable, signal))
  );

  if (signal?.aborted) {
    return;
  }

  canvas.clear();
  canvas.setZoom(zoom);
  canvas.setDimensions({
    width: Math.round(pageWidth * zoom),
    height: Math.round(pageHeight * zoom),
  });
  canvas.backgroundColor = design.page.backgroundColor || "#FFFFFF";
  canvas.backgroundImage = loadedBgImage;

  let objectToSelect: FabricObject | null = null;
  for (const obj of objects) {
    canvas.add(obj);
    const meta = getStudioObjectMeta(obj);
    if (meta && selectedElementId && meta.elementId === selectedElementId) {
      objectToSelect = obj;
    }
  }

  if (objectToSelect && objectToSelect.selectable) {
    canvas.setActiveObject(objectToSelect);
  }

  canvas.requestRenderAll();
}

export function extractElementUpdateFromFabricObject(
  obj: FabricObject,
  existing: DesignElement
): DesignElement {
  const left = round2(obj.left ?? existing.x);
  const top = round2(obj.top ?? existing.y);
  const scaledWidth = Math.max(1, round2(obj.getScaledWidth()));
  const scaledHeight = Math.max(1, round2(obj.getScaledHeight()));
  const rotation = normalizeAngle(obj.angle ?? existing.rotation);

  if (existing.type === "TEXT" && obj instanceof Textbox) {
    const scaleFactorY = obj.scaleY ?? 1;
    const nextFontSize =
      Math.abs(scaleFactorY - 1) > 0.01
        ? Math.min(200, Math.max(6, round2((existing.fontSize ?? 16) * scaleFactorY)))
        : (existing.fontSize ?? 16);

    return {
      ...existing,
      x: left,
      y: top,
      width: Math.max(24, scaledWidth),
      height: Math.max(16, scaledHeight),
      rotation,
      fontSize: nextFontSize,
      text: obj.text ?? existing.text ?? "",
    };
  }

  if (existing.type === "SHAPE" && existing.shapeKind === "LINE") {
    return {
      ...existing,
      x: left,
      y: top,
      width: Math.max(10, scaledWidth),
      height: Math.max(2, round2(existing.height)),
      rotation,
    };
  }

  return {
    ...existing,
    x: left,
    y: top,
    width: scaledWidth,
    height: scaledHeight,
    rotation,
  };
}

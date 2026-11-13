import type { DesignDocument, DesignElement, TemplateOrientation } from "../api/types.ts";
import { sanitizeDesignForValidation } from "./design-helpers.ts";

export interface CertificatePreset {
  id: string;
  name: string;
  category: string;
  styleTag: "Classic" | "Luxury" | "Modern" | "Minimal";
  accentHex: string;
  surfaceHex: string;
  description: string;
  build: (orientation: TemplateOrientation) => DesignDocument;
}

function pageDims(orientation: TemplateOrientation) {
  return orientation === "PORTRAIT" ? { width: 595, height: 842 } : { width: 842, height: 595 };
}

function rect(
  id: string,
  name: string,
  x: number,
  y: number,
  width: number,
  height: number,
  fill: string,
  zIndex: number,
  stroke?: string,
  strokeWidth = 0
): DesignElement {
  return {
    id,
    type: "SHAPE",
    name,
    x,
    y,
    width,
    height,
    rotation: 0,
    opacity: 1,
    visible: true,
    locked: false,
    zIndex,
    shapeKind: "RECTANGLE",
    fill,
    ...(stroke && /^#[0-9a-fA-F]{6}$/.test(stroke) ? { stroke } : {}),
    strokeWidth,
    cornerRadius: 0,
  };
}

function circle(
  id: string,
  name: string,
  x: number,
  y: number,
  size: number,
  fill: string,
  stroke: string,
  strokeWidth: number,
  zIndex: number
): DesignElement {
  return {
    id,
    type: "SHAPE",
    name,
    x,
    y,
    width: size,
    height: size,
    rotation: 0,
    opacity: 1,
    visible: true,
    locked: false,
    zIndex,
    shapeKind: "CIRCLE",
    fill,
    stroke,
    strokeWidth,
  };
}

function line(
  id: string,
  name: string,
  x: number,
  y: number,
  width: number,
  color: string,
  zIndex: number
): DesignElement {
  return {
    id,
    type: "SHAPE",
    name,
    x,
    y,
    width,
    height: 3,
    rotation: 0,
    opacity: 1,
    visible: true,
    locked: false,
    zIndex,
    shapeKind: "LINE",
    fill: color,
    stroke: color,
    strokeWidth: 2,
  };
}

function text(
  id: string,
  name: string,
  x: number,
  y: number,
  width: number,
  height: number,
  content: string,
  fontSize: number,
  fill: string,
  zIndex: number,
  options: {
    fontFamily?: string;
    fontWeight?: "NORMAL" | "BOLD";
    fontStyle?: "NORMAL" | "ITALIC";
    textAlign?: "LEFT" | "CENTER" | "RIGHT";
    lineHeight?: number;
  } = {}
): DesignElement {
  return {
    id,
    type: "TEXT",
    name,
    x,
    y,
    width,
    height,
    rotation: 0,
    opacity: 1,
    visible: true,
    locked: false,
    zIndex,
    text: content,
    fontFamily: options.fontFamily ?? "Inter",
    fontSize,
    fontWeight: options.fontWeight ?? "NORMAL",
    fontStyle: options.fontStyle ?? "NORMAL",
    fill,
    textAlign: options.textAlign ?? "CENTER",
    lineHeight: options.lineHeight ?? 1.25,
  };
}

function qr(
  id: string,
  x: number,
  y: number,
  size: number,
  fg: string,
  bg: string,
  zIndex: number
): DesignElement {
  return {
    id,
    type: "QR",
    name: "Verification QR",
    x,
    y,
    width: size,
    height: size,
    rotation: 0,
    opacity: 1,
    visible: true,
    locked: false,
    zIndex,
    qrDataTemplate: "{{verificationUrl}}",
    foregroundColor: fg,
    backgroundColor: bg,
  };
}

export const CERTIFICATE_STUDIO_PRESETS: CertificatePreset[] = [
  {
    id: "navy-gold-ribbon",
    name: "Royal Navy & Gold Seal",
    category: "Achievement",
    styleTag: "Classic",
    accentHex: "#142D4E",
    surfaceHex: "#FCFBF7",
    description: "Classic ivory backdrop with navy ribbons, gold framing, award seal, and QR.",
    build: (orientation) => {
      const { width, height } = pageDims(orientation);
      const portrait = orientation === "PORTRAIT";
      return sanitizeDesignForValidation({
        schemaVersion: "1.0",
        page: {
          width,
          height,
          unit: "pt",
          orientation,
          backgroundColor: "#FCFBF7",
          backgroundAssetId: null,
        },
        elements: [
          rect("top-ribbon", "Top Navy Band", 0, 0, width, 24, "#142D4E", 0),
          rect("bottom-ribbon", "Bottom Navy Band", 0, height - 24, width, 24, "#142D4E", 1),
          rect(
            "outer-frame",
            "Gold Outer Frame",
            24,
            34,
            width - 48,
            height - 68,
            "transparent",
            2,
            "#C6A45B",
            2.5
          ),
          rect(
            "inner-frame",
            "Navy Inner Frame",
            34,
            44,
            width - 68,
            height - 88,
            "transparent",
            3,
            "#142D4E",
            1
          ),
          circle(
            "seal-outer",
            "Gold Award Seal",
            width - 134,
            64,
            72,
            "#C6A45B",
            "#142D4E",
            2.5,
            4
          ),
          circle("seal-inner", "Seal Center", width - 122, 76, 48, "#142D4E", "#FCFBF7", 1.5, 5),
          text("seal-txt", "Seal Label", width - 122, 91, 48, 22, "AWARD", 9, "#FCFBF7", 6, {
            fontWeight: "BOLD",
          }),
          text(
            "org-kicker",
            "Organization Name",
            70,
            68,
            width - 220,
            22,
            "{{organizationName}}",
            12,
            "#C6A45B",
            7,
            {
              fontWeight: "BOLD",
              textAlign: "LEFT",
            }
          ),
          text(
            "title-main",
            "Heading",
            64,
            portrait ? 130 : 108,
            width - 128,
            42,
            "CERTIFICATE",
            34,
            "#142D4E",
            8,
            {
              fontFamily: "Georgia",
              fontWeight: "BOLD",
            }
          ),
          text(
            "title-sub",
            "Subtitle",
            64,
            portrait ? 174 : 150,
            width - 128,
            24,
            "OF ACHIEVEMENT",
            14,
            "#475569",
            9,
            {
              fontWeight: "BOLD",
            }
          ),
          text(
            "kicker",
            "Presented Label",
            64,
            portrait ? 236 : 196,
            width - 128,
            22,
            "THIS CERTIFICATE IS PROUDLY PRESENTED TO",
            11,
            "#64748B",
            10
          ),
          text(
            "recipient",
            "Recipient Name",
            64,
            portrait ? 276 : 230,
            width - 128,
            56,
            "{{recipientName}}",
            32,
            "#142D4E",
            11,
            {
              fontFamily: "Georgia",
              fontStyle: "ITALIC",
              fontWeight: "BOLD",
            }
          ),
          line(
            "name-rule",
            "Recipient Underline",
            (width - 340) / 2,
            portrait ? 344 : 294,
            340,
            "#C6A45B",
            12
          ),
          text(
            "course-body",
            "Course Description",
            84,
            portrait ? 378 : 318,
            width - 168,
            56,
            "for successfully completing {{courseName}} with distinction and verified excellence.",
            14,
            "#334155",
            13,
            { lineHeight: 1.45 }
          ),
          line("date-rule", "Date Line", 84, height - 118, 160, "#64748B", 14),
          text(
            "date-val",
            "Issued Date",
            84,
            height - 144,
            160,
            22,
            "{{issuedDate}}",
            11,
            "#0F172A",
            15,
            {
              fontWeight: "BOLD",
            }
          ),
          text(
            "date-lbl",
            "Date Label",
            84,
            height - 110,
            160,
            18,
            "ISSUED DATE",
            9,
            "#64748B",
            16
          ),
          line("sig-rule", "Signature Line", width / 2 - 80, height - 118, 160, "#64748B", 17),
          text(
            "sig-val",
            "Signatory",
            width / 2 - 80,
            height - 144,
            160,
            22,
            "{{issuerName}}",
            12,
            "#142D4E",
            18,
            {
              fontFamily: "Georgia",
              fontStyle: "ITALIC",
            }
          ),
          text(
            "sig-lbl",
            "Signature Label",
            width / 2 - 80,
            height - 110,
            160,
            18,
            "AUTHORIZED SIGNATURE",
            9,
            "#64748B",
            19
          ),
          qr("qr-verify", width - 142, height - 146, 76, "#142D4E", "#FCFBF7", 20),
          text(
            "cert-num",
            "Certificate ID",
            width - 170,
            height - 64,
            120,
            16,
            "{{certificateNumber}}",
            8,
            "#64748B",
            21
          ),
        ],
      });
    },
  },
  {
    id: "midnight-luxury-gold",
    name: "Midnight Obsidian & Gold",
    category: "Appreciation",
    styleTag: "Luxury",
    accentHex: "#D4AF37",
    surfaceHex: "#0F172A",
    description: "Executive dark obsidian background with metallic gold border & typography.",
    build: (orientation) => {
      const { width, height } = pageDims(orientation);
      const portrait = orientation === "PORTRAIT";
      return sanitizeDesignForValidation({
        schemaVersion: "1.0",
        page: {
          width,
          height,
          unit: "pt",
          orientation,
          backgroundColor: "#0F172A",
          backgroundAssetId: null,
        },
        elements: [
          rect("gold-edge-left", "Gold Left Accent", 0, 0, 18, height, "#D4AF37", 0),
          rect("gold-edge-bottom", "Gold Bottom Accent", 0, height - 16, width, 16, "#D4AF37", 1),
          rect(
            "outer-gold",
            "Outer Gold Border",
            32,
            26,
            width - 64,
            height - 62,
            "transparent",
            2,
            "#D4AF37",
            2
          ),
          circle("crest", "Best Award Seal", 68, 56, 74, "#D4AF37", "#FDE68A", 2, 3),
          circle("crest-core", "Crest Inner", 80, 68, 50, "#0F172A", "#D4AF37", 1.5, 4),
          text("crest-txt", "Crest Title", 80, 84, 50, 22, "BEST\n2026", 8, "#FDE68A", 5, {
            fontWeight: "BOLD",
            lineHeight: 1.15,
          }),
          text(
            "heading",
            "Certificate Title",
            156,
            60,
            width - 210,
            40,
            "CERTIFICATE",
            32,
            "#FFFFFF",
            6,
            {
              fontFamily: "Georgia",
              fontWeight: "BOLD",
              textAlign: "LEFT",
            }
          ),
          text("sub", "Subtitle", 158, 102, width - 210, 22, "OF APPRECIATION", 13, "#D4AF37", 7, {
            fontWeight: "BOLD",
            textAlign: "LEFT",
          }),
          text(
            "intro",
            "Presented Label",
            68,
            portrait ? 210 : 172,
            width - 136,
            20,
            "PROUDLY PRESENTED TO",
            11,
            "#CBD5E1",
            8,
            {
              fontWeight: "BOLD",
            }
          ),
          text(
            "recipient",
            "Recipient Name",
            68,
            portrait ? 250 : 206,
            width - 136,
            56,
            "{{recipientName}}",
            34,
            "#EAB308",
            9,
            {
              fontFamily: "Georgia",
              fontStyle: "ITALIC",
              fontWeight: "BOLD",
            }
          ),
          text(
            "desc",
            "Description",
            96,
            portrait ? 340 : 284,
            width - 192,
            56,
            "In recognition of outstanding dedication and achievement in {{courseName}} awarded by {{organizationName}}.",
            13,
            "#CBD5E1",
            10,
            { lineHeight: 1.45 }
          ),
          line("l1", "Date Line", 76, height - 114, 160, "#E2E8F0", 11),
          text("d1", "Date Value", 76, height - 138, 160, 20, "{{issuedDate}}", 11, "#FFFFFF", 12),
          text("d2", "Date Caption", 76, height - 106, 160, 16, "DATE", 9, "#94A3B8", 13),
          line("l2", "Signature Line", width / 2 - 70, height - 114, 160, "#E2E8F0", 14),
          text(
            "s1",
            "Signatory Value",
            width / 2 - 70,
            height - 138,
            160,
            20,
            "{{issuerName}}",
            12,
            "#D4AF37",
            15,
            {
              fontFamily: "Georgia",
              fontStyle: "ITALIC",
            }
          ),
          text(
            "s2",
            "Signature Caption",
            width / 2 - 70,
            height - 106,
            160,
            16,
            "SIGNATURE",
            9,
            "#94A3B8",
            16
          ),
          qr("qr", width - 134, height - 144, 72, "#0F172A", "#FFFFFF", 17),
          text(
            "cid",
            "Certificate Number",
            width - 160,
            height - 66,
            124,
            16,
            "{{certificateNumber}}",
            8,
            "#CBD5E1",
            18
          ),
        ],
      });
    },
  },
  {
    id: "modern-split-navy",
    name: "Modern Split Sidebar",
    category: "Completion",
    styleTag: "Modern",
    accentHex: "#142D4E",
    surfaceHex: "#FFFFFF",
    description: "Bold navy left identity column paired with crisp structured typography.",
    build: (orientation) => {
      const { width, height } = pageDims(orientation);
      const sidebarW = orientation === "PORTRAIT" ? 140 : 210;
      const contentX = sidebarW + 44;
      const contentW = width - contentX - 48;
      return sanitizeDesignForValidation({
        schemaVersion: "1.0",
        page: {
          width,
          height,
          unit: "pt",
          orientation,
          backgroundColor: "#FFFFFF",
          backgroundAssetId: null,
        },
        elements: [
          rect("sidebar", "Left Navy Panel", 0, 0, sidebarW, height, "#142D4E", 0),
          rect("gold-strip", "Gold Divider", sidebarW, 0, 5, height, "#C6A45B", 1),
          text(
            "org-side",
            "Organization",
            20,
            64,
            sidebarW - 40,
            60,
            "{{organizationName}}",
            15,
            "#FFFFFF",
            2,
            {
              fontWeight: "BOLD",
              lineHeight: 1.35,
            }
          ),
          qr("qr-side", (sidebarW - 88) / 2, height - 148, 88, "#142D4E", "#FFFFFF", 3),
          text(
            "cid-side",
            "Cert Number",
            18,
            height - 50,
            sidebarW - 36,
            20,
            "{{certificateNumber}}",
            9,
            "#CBD5E1",
            4
          ),
          circle("badge", "Gold Badge", width - 116, 48, 66, "#FACC15", "#142D4E", 2, 5),
          text("badge-yr", "Badge Text", width - 110, 72, 54, 22, "VERIFIED", 8, "#142D4E", 6, {
            fontWeight: "BOLD",
          }),
          text("title", "Title", contentX, 62, contentW - 80, 44, "CERTIFICATE", 34, "#0F172A", 7, {
            fontWeight: "BOLD",
            textAlign: "LEFT",
          }),
          text(
            "subtitle",
            "Subtitle",
            contentX,
            108,
            contentW - 80,
            26,
            "OF COMPLETION",
            16,
            "#142D4E",
            8,
            {
              fontWeight: "BOLD",
              textAlign: "LEFT",
            }
          ),
          text(
            "presented",
            "Presented Text",
            contentX,
            180,
            contentW,
            22,
            "We proudly present this credential to",
            12,
            "#64748B",
            9,
            {
              textAlign: "LEFT",
            }
          ),
          text(
            "recipient",
            "Recipient Name",
            contentX,
            212,
            contentW,
            52,
            "{{recipientName}}",
            32,
            "#142D4E",
            10,
            {
              fontWeight: "BOLD",
              textAlign: "LEFT",
            }
          ),
          line("rule", "Accent Line", contentX, 272, contentW * 0.75, "#E2E8F0", 11),
          text(
            "course",
            "Course Line",
            contentX,
            296,
            contentW,
            56,
            "honoring successful completion of {{courseName}} and all practical assessments.",
            13,
            "#334155",
            12,
            { textAlign: "LEFT", lineHeight: 1.45 }
          ),
          line("sig-line", "Signature Line", contentX, height - 110, 175, "#94A3B8", 13),
          text(
            "sig-name",
            "Signatory",
            contentX,
            height - 136,
            175,
            22,
            "{{issuerName}}",
            13,
            "#0F172A",
            14,
            {
              fontFamily: "Georgia",
              fontStyle: "ITALIC",
              textAlign: "LEFT",
            }
          ),
          text(
            "sig-cap",
            "Role",
            contentX,
            height - 102,
            175,
            18,
            "PROGRAM DIRECTOR",
            9,
            "#64748B",
            15,
            {
              textAlign: "LEFT",
            }
          ),
          text(
            "date-cap",
            "Issued On",
            contentX + 220,
            height - 102,
            160,
            18,
            "ISSUED: {{issuedDate}}",
            10,
            "#475569",
            16,
            {
              textAlign: "LEFT",
              fontWeight: "BOLD",
            }
          ),
        ],
      });
    },
  },
  {
    id: "clean-academic-coral",
    name: "Clean Corporate Ribbon",
    category: "Course",
    styleTag: "Minimal",
    accentHex: "#E07A5F",
    surfaceHex: "#FFFFFF",
    description: "Minimal Scandinavian layout with structured metadata bar and crisp hierarchy.",
    build: (orientation) => {
      const { width, height } = pageDims(orientation);
      return sanitizeDesignForValidation({
        schemaVersion: "1.0",
        page: {
          width,
          height,
          unit: "pt",
          orientation,
          backgroundColor: "#FFFFFF",
          backgroundAssetId: null,
        },
        elements: [
          rect(
            "border",
            "Subtle Frame",
            24,
            24,
            width - 48,
            height - 48,
            "transparent",
            0,
            "#CBD5E1",
            1.5
          ),
          rect("band", "Metadata Accent Band", 44, height - 208, width - 88, 52, "#F6A78C", 1),
          text("title", "Title", 56, 62, width - 112, 42, "CERTIFICATE", 34, "#0F172A", 2, {
            fontWeight: "BOLD",
            textAlign: "LEFT",
          }),
          text(
            "sub",
            "Subtitle",
            56,
            108,
            width - 112,
            24,
            "OF COURSE COMPLETION",
            15,
            "#1E293B",
            3,
            {
              fontWeight: "BOLD",
              textAlign: "LEFT",
            }
          ),
          text(
            "lbl",
            "Presented Label",
            56,
            168,
            width - 112,
            20,
            "PROUDLY PRESENTED TO",
            10,
            "#64748B",
            4,
            {
              textAlign: "LEFT",
            }
          ),
          text(
            "recipient",
            "Recipient Name",
            56,
            198,
            width - 112,
            48,
            "{{recipientName}}",
            32,
            "#0F172A",
            5,
            {
              fontWeight: "BOLD",
              textAlign: "LEFT",
            }
          ),
          text(
            "desc",
            "Course Line",
            56,
            262,
            width - 140,
            48,
            "HAS DEMONSTRATED PROFESSIONAL COMPETENCE BY COMPLETING {{courseName}}",
            12,
            "#334155",
            6,
            { textAlign: "LEFT", lineHeight: 1.45 }
          ),
          text(
            "band-date",
            "Date Cell",
            64,
            height - 196,
            170,
            28,
            "{{issuedDate}}\nDATE OF ISSUE",
            9,
            "#0F172A",
            7,
            {
              textAlign: "LEFT",
              fontWeight: "BOLD",
            }
          ),
          text(
            "band-org",
            "Org Cell",
            width / 2 - 80,
            height - 196,
            180,
            28,
            "{{organizationName}}\nISSUING AUTHORITY",
            9,
            "#0F172A",
            8,
            {
              textAlign: "LEFT",
              fontWeight: "BOLD",
            }
          ),
          text(
            "band-id",
            "ID Cell",
            width - 220,
            height - 196,
            160,
            28,
            "{{certificateNumber}}\nREFERENCE ID",
            9,
            "#0F172A",
            9,
            {
              textAlign: "LEFT",
              fontWeight: "BOLD",
            }
          ),
          line("sig-line", "Signature Rule", 56, height - 88, 170, "#64748B", 10),
          text(
            "sig-val",
            "Signatory",
            56,
            height - 116,
            170,
            24,
            "{{issuerName}}",
            13,
            "#0F172A",
            11,
            {
              fontFamily: "Georgia",
              fontStyle: "ITALIC",
              textAlign: "LEFT",
            }
          ),
          text(
            "sig-cap",
            "Director Label",
            56,
            height - 80,
            170,
            16,
            "CERTIFICATION COORDINATOR",
            8,
            "#64748B",
            12,
            {
              textAlign: "LEFT",
            }
          ),
          qr("qr", width - 134, height - 134, 72, "#0F172A", "#FFFFFF", 13),
        ],
      });
    },
  },
  {
    id: "emerald-heritage-banner",
    name: "Emerald Heritage Ribbon",
    category: "Training",
    styleTag: "Classic",
    accentHex: "#0F5132",
    surfaceHex: "#FBF9F4",
    description:
      "Warm cream parchment with deep emerald side banner, gold border, and Khmer ready.",
    build: (orientation) => {
      const { width, height } = pageDims(orientation);
      const bannerW = orientation === "PORTRAIT" ? 110 : 150;
      const rightX = width - bannerW - 36;
      const bodyW = rightX - 68;
      return sanitizeDesignForValidation({
        schemaVersion: "1.0",
        page: {
          width,
          height,
          unit: "pt",
          orientation,
          backgroundColor: "#FBF9F4",
          backgroundAssetId: null,
        },
        elements: [
          rect(
            "outer-gold",
            "Gold Inner Frame",
            24,
            24,
            width - 48,
            height - 48,
            "transparent",
            0,
            "#C6A45B",
            2
          ),
          rect(
            "emerald-col",
            "Emerald Side Ribbon",
            rightX,
            24,
            bannerW,
            height - 48,
            "#0F5132",
            1
          ),
          circle(
            "emblem",
            "Gold Seal",
            rightX + (bannerW - 64) / 2,
            56,
            64,
            "#C6A45B",
            "#FBF9F4",
            2,
            2
          ),
          text(
            "emblem-yr",
            "Seal Year",
            rightX + (bannerW - 64) / 2,
            80,
            64,
            20,
            "2026",
            12,
            "#0F5132",
            3,
            {
              fontWeight: "BOLD",
            }
          ),
          qr("qr", rightX + (bannerW - 76) / 2, height - 130, 76, "#0F5132", "#FFFFFF", 4),
          text("org", "Organization", 52, 58, bodyW, 22, "{{organizationName}}", 12, "#0F5132", 5, {
            fontWeight: "BOLD",
            textAlign: "LEFT",
          }),
          text("title", "Heading", 52, 102, bodyW, 40, "CERTIFICATE", 30, "#0F172A", 6, {
            fontFamily: "Georgia",
            fontWeight: "BOLD",
            textAlign: "LEFT",
          }),
          text(
            "khmer-sub",
            "Khmer Subtitle",
            52,
            146,
            bodyW,
            30,
            "វិញ្ញាបនបត្របញ្ចប់វគ្គសិក្សា",
            16,
            "#0F5132",
            7,
            {
              fontFamily: "Noto Sans Khmer",
              fontWeight: "BOLD",
              textAlign: "LEFT",
            }
          ),
          text(
            "presented",
            "Label",
            52,
            206,
            bodyW,
            20,
            "THIS CERTIFICATE IS PROUDLY PRESENTED TO",
            10,
            "#64748B",
            8,
            {
              textAlign: "LEFT",
            }
          ),
          text(
            "recipient",
            "Recipient Name",
            52,
            236,
            bodyW,
            50,
            "{{recipientName}}",
            30,
            "#0F5132",
            9,
            {
              fontFamily: "Georgia",
              fontStyle: "ITALIC",
              fontWeight: "BOLD",
              textAlign: "LEFT",
            }
          ),
          text(
            "desc",
            "Description",
            52,
            306,
            bodyW,
            52,
            "for outstanding achievement and completion of {{courseName}}.",
            13,
            "#334155",
            10,
            { textAlign: "LEFT", lineHeight: 1.45 }
          ),
          line("date-rule", "Date Line", 52, height - 102, 140, "#94A3B8", 11),
          text(
            "date-val",
            "Date Value",
            52,
            height - 126,
            140,
            20,
            "{{issuedDate}}",
            11,
            "#0F172A",
            12,
            {
              textAlign: "LEFT",
            }
          ),
          text("date-cap", "Date Caption", 52, height - 94, 140, 16, "DATE", 9, "#64748B", 13, {
            textAlign: "LEFT",
          }),
          line("sig-rule", "Signature Line", 232, height - 102, 160, "#94A3B8", 14),
          text(
            "sig-val",
            "Signatory",
            232,
            height - 126,
            160,
            20,
            "{{issuerName}}",
            12,
            "#0F5132",
            15,
            {
              fontFamily: "Georgia",
              fontStyle: "ITALIC",
              textAlign: "LEFT",
            }
          ),
          text(
            "sig-cap",
            "Signature Caption",
            232,
            height - 94,
            160,
            16,
            "DIRECTOR SIGNATURE",
            9,
            "#64748B",
            16,
            {
              textAlign: "LEFT",
            }
          ),
        ],
      });
    },
  },
];

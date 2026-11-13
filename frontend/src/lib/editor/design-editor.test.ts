import assert from "node:assert/strict";
import test from "node:test";
import type { DesignDocument } from "../api/types.ts";
import {
  alignElementToPage,
  createDefaultElement,
  duplicateElement,
  reorderElementLayer,
  sanitizeDesignForValidation,
} from "./design-helpers.ts";
import {
  createHistoryState,
  pushHistorySnapshot,
  redoHistory,
  undoHistory,
} from "./design-history.ts";
import { CERTIFICATE_STUDIO_PRESETS } from "./design-presets.ts";

function makeSampleDoc(): DesignDocument {
  return {
    schemaVersion: "1.0",
    page: {
      width: 842,
      height: 595,
      unit: "pt",
      orientation: "LANDSCAPE",
      backgroundColor: "#ffffff",
      backgroundAssetId: null,
    },
    elements: [],
  };
}

test("history stack supports undo and redo and truncates redo branch on new edit", () => {
  const initial = makeSampleDoc();
  const title = createDefaultElement("TEXT", initial.page, 0, {
    text: "Certificate",
    fontFamily: "Inter",
  });
  const khmer = createDefaultElement("TEXT", initial.page, 1, {
    text: "វិញ្ញាបនបត្រ",
    fontFamily: "Noto Sans Khmer",
  });

  let state = createHistoryState(initial);
  state = pushHistorySnapshot(state, { ...initial, elements: [title] });
  state = pushHistorySnapshot(state, { ...initial, elements: [title, khmer] });
  assert.equal(state.present.elements.length, 2);

  state = undoHistory(state);
  assert.equal(state.present.elements.length, 1);
  assert.equal(state.present.elements[0].text, "Certificate");

  state = redoHistory(state);
  assert.equal(state.present.elements.length, 2);
  assert.equal(state.present.elements[1].fontFamily, "Noto Sans Khmer");

  state = undoHistory(state);
  const qr = createDefaultElement("QR", initial.page, 1);
  state = pushHistorySnapshot(state, { ...state.present, elements: [title, qr] });
  assert.equal(state.future.length, 0);
  assert.equal(state.present.elements[1].type, "QR");
});

test("layer reordering, alignment, duplication, and sanitization maintain canonical schema constraints", () => {
  const doc = makeSampleDoc();
  const el1 = createDefaultElement("SHAPE", doc.page, 0, { shapeKind: "RECTANGLE" });
  const el2 = createDefaultElement("TEXT", doc.page, 1, { text: "{{recipientName}}" });
  const copy = duplicateElement(el2, 2);
  assert.notEqual(copy.id, el2.id);
  assert.equal(copy.locked, false);

  const movedBack = reorderElementLayer([el1, el2, copy], copy.id, "back");
  assert.equal(movedBack[0].id, copy.id);
  assert.deepEqual(
    movedBack.map((e) => e.zIndex),
    [0, 1, 2]
  );

  const centered = alignElementToPage(el2, doc.page, "center-x");
  assert.equal(centered.x, (doc.page.width - centered.width) / 2);

  const badElement = {
    ...el1,
    width: 0,
    height: -5,
    rotation: 405,
    opacity: 1.5,
    stroke: "transparent",
  };
  const sanitized = sanitizeDesignForValidation({
    ...doc,
    elements: [badElement],
  });
  assert.equal(sanitized.page.backgroundColor, "#FFFFFF");
  assert.equal(sanitized.elements[0].width, 1);
  assert.equal(sanitized.elements[0].height, 1);
  assert.equal(sanitized.elements[0].rotation, 45);
  assert.equal(sanitized.elements[0].opacity, 1);
  assert.equal(sanitized.elements[0].stroke, undefined);
});

const HEX_COLOR = /^#[0-9A-F]{6}$/i;
const ALLOWED_FONTS = new Set([
  "Inter",
  "Noto Sans Khmer",
  "Georgia",
  "Times New Roman",
  "Courier New",
]);

test("starter certificate presets satisfy all backend DesignSchemaValidator constraints", () => {
  assert.equal(CERTIFICATE_STUDIO_PRESETS.length, 5);
  for (const preset of CERTIFICATE_STUDIO_PRESETS) {
    for (const orientation of ["LANDSCAPE", "PORTRAIT"] as const) {
      const doc = preset.build(orientation);
      assert.equal(doc.page.orientation, orientation);
      assert.equal(doc.schemaVersion, "1.0");
      assert.match(doc.page.backgroundColor, HEX_COLOR, `${preset.id} page bg`);
      assert.ok(doc.elements.length >= 10, `${preset.id} should include layered elements`);
      assert.ok(
        doc.elements.some((el) => el.type === "QR"),
        `${preset.id} should include a verification QR element`
      );
      assert.ok(
        doc.elements.some((el) => el.type === "TEXT" && el.text?.includes("{{recipientName}}")),
        `${preset.id} should include recipientName placeholder`
      );

      for (const el of doc.elements) {
        assert.ok(el.width > 0 && el.height > 0, `${preset.id}:${el.id} dimensions positive`);
        if (el.type === "SHAPE") {
          if (
            el.fill !== undefined &&
            el.fill !== null &&
            el.fill.toLowerCase() !== "transparent"
          ) {
            assert.match(el.fill, HEX_COLOR, `${preset.id}:${el.id} shape fill`);
          }
          if (el.stroke !== undefined && el.stroke !== null) {
            assert.match(el.stroke, HEX_COLOR, `${preset.id}:${el.id} shape stroke must be hex`);
          }
        }
        if (el.type === "TEXT") {
          assert.ok(
            el.fontFamily && ALLOWED_FONTS.has(el.fontFamily),
            `${preset.id}:${el.id} font`
          );
          assert.match(el.fill ?? "", HEX_COLOR, `${preset.id}:${el.id} text fill`);
        }
        if (el.type === "QR") {
          assert.match(el.foregroundColor ?? "", HEX_COLOR, `${preset.id}:${el.id} qr fg`);
          assert.match(el.backgroundColor ?? "", HEX_COLOR, `${preset.id}:${el.id} qr bg`);
        }
      }
    }
  }
});

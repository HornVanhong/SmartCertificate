"use client";

import * as React from "react";
import { Canvas } from "fabric";
import type { DesignDocument, DesignElement } from "@/lib/api/types";
import {
  extractElementUpdateFromFabricObject,
  getStudioObjectMeta,
  renderDesignToFabricCanvas,
} from "@/lib/editor/fabric-adapter";

interface FabricCanvasStageProps {
  design: DesignDocument;
  assetUrls: Record<string, string>;
  editable: boolean;
  selectedElementId: string | null;
  zoom: number;
  showGuides: boolean;
  onSelectElement: (elementId: string | null) => void;
  onCommitElementChange: (updatedElement: DesignElement) => void;
  onDeleteSelected: () => void;
  onDuplicateSelected: () => void;
  onUndo: () => void;
  onRedo: () => void;
  onSaveShortcut: () => void;
}

export function FabricCanvasStage({
  design,
  assetUrls,
  editable,
  selectedElementId,
  zoom,
  showGuides,
  onSelectElement,
  onCommitElementChange,
  onDeleteSelected,
  onDuplicateSelected,
  onUndo,
  onRedo,
  onSaveShortcut,
}: FabricCanvasStageProps) {
  const canvasElRef = React.useRef<HTMLCanvasElement | null>(null);
  const fabricRef = React.useRef<Canvas | null>(null);
  const isProgrammaticSyncRef = React.useRef(false);
  const isEditingInlineTextRef = React.useRef(false);

  // Keep latest callbacks in refs so Fabric event listeners never hold stale closures.
  const latestRef = React.useRef({
    design,
    onSelectElement,
    onCommitElementChange,
    onDeleteSelected,
    onDuplicateSelected,
    onUndo,
    onRedo,
    onSaveShortcut,
    editable,
  });

  React.useEffect(() => {
    latestRef.current = {
      design,
      onSelectElement,
      onCommitElementChange,
      onDeleteSelected,
      onDuplicateSelected,
      onUndo,
      onRedo,
      onSaveShortcut,
      editable,
    };
  });

  // Initialize Fabric.Canvas once per mount
  React.useEffect(() => {
    const el = canvasElRef.current;
    if (!el) return;

    const canvas = new Canvas(el, {
      preserveObjectStacking: true,
      selection: true,
      stopContextMenu: true,
    });
    fabricRef.current = canvas;

    const handleSelection = (selected: {
      selected?: Array<Parameters<typeof getStudioObjectMeta>[0]>;
    }) => {
      if (isProgrammaticSyncRef.current) return;
      const first = selected.selected?.[0] ?? canvas.getActiveObject();
      const meta = getStudioObjectMeta(first);
      latestRef.current.onSelectElement(meta?.elementId ?? null);
    };

    const handleCleared = () => {
      if (isProgrammaticSyncRef.current) return;
      latestRef.current.onSelectElement(null);
    };

    const handleModified = (evt: { target?: Parameters<typeof getStudioObjectMeta>[0] }) => {
      if (isProgrammaticSyncRef.current || !evt.target) return;
      const meta = getStudioObjectMeta(evt.target);
      if (!meta) return;
      const existing = latestRef.current.design.elements.find((item) => item.id === meta.elementId);
      if (!existing) return;
      const updated = extractElementUpdateFromFabricObject(evt.target, existing);
      latestRef.current.onCommitElementChange(updated);
    };

    const handleEditingEntered = () => {
      isEditingInlineTextRef.current = true;
    };

    const handleEditingExited = (evt: { target?: Parameters<typeof getStudioObjectMeta>[0] }) => {
      isEditingInlineTextRef.current = false;
      if (evt.target) {
        handleModified({ target: evt.target });
      }
    };

    canvas.on("selection:created", handleSelection);
    canvas.on("selection:updated", handleSelection);
    canvas.on("selection:cleared", handleCleared);
    canvas.on("object:modified", handleModified);
    canvas.on("text:editing:entered", handleEditingEntered);
    canvas.on("text:editing:exited", handleEditingExited);

    return () => {
      canvas.dispose();
      fabricRef.current = null;
    };
  }, []);

  // Re-render Fabric canvas whenever canonical design, asset URLs, zoom, editability, or selection changes.
  React.useEffect(() => {
    const canvas = fabricRef.current;
    if (!canvas || isEditingInlineTextRef.current) {
      return;
    }

    const controller = new AbortController();
    isProgrammaticSyncRef.current = true;

    renderDesignToFabricCanvas({
      canvas,
      design,
      assetUrls,
      editable,
      selectedElementId,
      zoom,
      signal: controller.signal,
    })
      .catch(() => {
        // Ignore aborted renders during rapid state updates
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          isProgrammaticSyncRef.current = false;
        }
      });

    return () => {
      controller.abort();
      isProgrammaticSyncRef.current = false;
    };
  }, [design, assetUrls, editable, selectedElementId, zoom]);

  // Keyboard shortcuts
  React.useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      const isInputFocused =
        target &&
        (target.tagName === "INPUT" ||
          target.tagName === "TEXTAREA" ||
          target.tagName === "SELECT" ||
          target.isContentEditable);

      if (isInputFocused || isEditingInlineTextRef.current) {
        return;
      }

      const mod = event.ctrlKey || event.metaKey;
      if (mod && event.key.toLowerCase() === "s") {
        event.preventDefault();
        latestRef.current.onSaveShortcut();
        return;
      }

      if (!latestRef.current.editable) return;

      if (mod && event.key.toLowerCase() === "z" && !event.shiftKey) {
        event.preventDefault();
        latestRef.current.onUndo();
        return;
      }
      if (
        (mod && event.key.toLowerCase() === "y") ||
        (mod && event.shiftKey && event.key.toLowerCase() === "z")
      ) {
        event.preventDefault();
        latestRef.current.onRedo();
        return;
      }
      if (mod && event.key.toLowerCase() === "d") {
        event.preventDefault();
        latestRef.current.onDuplicateSelected();
        return;
      }
      if (event.key === "Delete" || event.key === "Backspace") {
        event.preventDefault();
        latestRef.current.onDeleteSelected();
        return;
      }

      if (
        selectedElementId &&
        ["ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight"].includes(event.key)
      ) {
        const element = latestRef.current.design.elements.find((el) => el.id === selectedElementId);
        if (!element || element.locked) return;
        event.preventDefault();
        const step = event.shiftKey ? 10 : 1;
        const dx = event.key === "ArrowLeft" ? -step : event.key === "ArrowRight" ? step : 0;
        const dy = event.key === "ArrowUp" ? -step : event.key === "ArrowDown" ? step : 0;
        latestRef.current.onCommitElementChange({
          ...element,
          x: element.x + dx,
          y: element.y + dy,
        });
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [selectedElementId]);

  const stageWidth = Math.round(design.page.width * zoom);
  const stageHeight = Math.round(design.page.height * zoom);
  const safeInset = Math.round(28 * zoom);

  return (
    <div className="flex flex-1 flex-col overflow-hidden rounded-xl border border-border-subtle bg-studio-dots">
      <div className="flex-1 overflow-auto p-6">
        <div className="mx-auto flex min-h-full w-fit items-center justify-center">
          <div
            style={{ width: `${stageWidth}px`, height: `${stageHeight}px` }}
            className="relative bg-white shadow-xl ring-1 ring-slate-900/15"
          >
            <canvas ref={canvasElRef} />

            {showGuides ? (
              <div className="pointer-events-none absolute inset-0">
                <div
                  style={{
                    top: `${safeInset}px`,
                    right: `${safeInset}px`,
                    bottom: `${safeInset}px`,
                    left: `${safeInset}px`,
                  }}
                  className="absolute border border-dashed border-brand-blue/45"
                />
                <div className="absolute top-0 bottom-0 left-1/2 w-px border-l border-dashed border-brand-gold/55" />
                <div className="absolute top-1/2 right-0 left-0 h-px border-t border-dashed border-brand-gold/55" />
              </div>
            ) : null}
          </div>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border-subtle bg-surface px-4 py-1.5 text-[11px] text-text-secondary">
        <span>
          Page: {design.page.orientation} · {design.page.width} × {design.page.height}{" "}
          {design.page.unit}
        </span>
        <span>
          Zoom: {Math.round(zoom * 100)}% · Shortcuts: Ctrl+Z Undo · Ctrl+D Duplicate · Arrows Nudge
        </span>
      </div>
    </div>
  );
}

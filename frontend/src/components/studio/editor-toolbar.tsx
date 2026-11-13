"use client";

import Link from "next/link";
import {
  ArrowLeft,
  CheckCircle2,
  Code2,
  Copy,
  Eye,
  Grid,
  History,
  Loader2,
  Redo2,
  Save,
  Undo2,
  ZoomIn,
  ZoomOut,
} from "lucide-react";
import { TemplateStatusBadge } from "@/components/templates/template-status-badge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import type { TemplateStatus } from "@/lib/api/types";

export type SaveIndicatorState = "saved" | "dirty" | "saving" | "autosaved" | "error";

interface EditorToolbarProps {
  templateName: string;
  onNameChange: (name: string) => void;
  status: TemplateStatus;
  currentVersion: number;
  publishedVersion: number | null;
  canEdit: boolean;
  canWrite: boolean;
  canPublish: boolean;
  saveState: SaveIndicatorState;
  canUndo: boolean;
  canRedo: boolean;
  zoom: number;
  showGuides: boolean;
  busyAction: boolean;
  onUndo: () => void;
  onRedo: () => void;
  onZoomChange: (zoom: number) => void;
  onToggleGuides: () => void;
  onOpenPreview: () => void;
  onOpenHistory: () => void;
  onToggleJsonModal: () => void;
  onSaveDraft: () => void;
  onPublish: () => void;
  onDuplicate: () => void;
}

const SAVE_STATUS_LABELS: Record<SaveIndicatorState, string> = {
  saved: "All changes saved",
  dirty: "Unsaved changes",
  saving: "Saving draft...",
  autosaved: "Autosaved",
  error: "Save error",
};

export function EditorToolbar({
  templateName,
  onNameChange,
  status,
  currentVersion,
  publishedVersion,
  canEdit,
  canWrite,
  canPublish,
  saveState,
  canUndo,
  canRedo,
  zoom,
  showGuides,
  busyAction,
  onUndo,
  onRedo,
  onZoomChange,
  onToggleGuides,
  onOpenPreview,
  onOpenHistory,
  onToggleJsonModal,
  onSaveDraft,
  onPublish,
  onDuplicate,
}: EditorToolbarProps) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border-subtle bg-surface px-4 py-2.5 shadow-2xs">
      <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2.5">
        <Link
          href="/studio/templates"
          className="inline-flex h-8 items-center gap-1 rounded-lg border border-border-subtle px-2.5 text-xs font-medium text-text-secondary transition-colors hover:bg-slate-50 hover:text-text-main"
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          Gallery
        </Link>

        <Input
          aria-label="Template name"
          value={templateName}
          disabled={!canEdit}
          onChange={(e) => onNameChange(e.target.value)}
          className="h-8 max-w-64 font-semibold"
        />

        <TemplateStatusBadge status={status} />
        <Badge variant="neutral">v{currentVersion}</Badge>
        {publishedVersion ? <Badge variant="gold">Live v{publishedVersion}</Badge> : null}

        <span
          className={`text-xs font-medium ${
            saveState === "error"
              ? "text-rose-600"
              : saveState === "dirty"
                ? "text-amber-700"
                : "text-text-secondary"
          }`}
        >
          {SAVE_STATUS_LABELS[saveState]}
        </span>
      </div>

      <div className="flex flex-wrap items-center gap-1.5">
        <Button
          type="button"
          variant="outline"
          size="sm"
          title="Undo (Ctrl+Z)"
          disabled={!canEdit || !canUndo}
          onClick={onUndo}
          className="h-8 px-2.5"
        >
          <Undo2 className="h-3.5 w-3.5" />
        </Button>
        <Button
          type="button"
          variant="outline"
          size="sm"
          title="Redo (Ctrl+Y)"
          disabled={!canEdit || !canRedo}
          onClick={onRedo}
          className="h-8 px-2.5"
        >
          <Redo2 className="h-3.5 w-3.5" />
        </Button>

        <div className="mx-1 h-5 w-px bg-border-subtle" />

        <Button
          type="button"
          variant="outline"
          size="sm"
          title="Zoom out"
          onClick={() => onZoomChange(Math.max(0.35, Math.round((zoom - 0.1) * 100) / 100))}
          className="h-8 px-2"
        >
          <ZoomOut className="h-3.5 w-3.5" />
        </Button>
        <select
          aria-label="Canvas zoom"
          value={String(Math.round(zoom * 100))}
          onChange={(e) => onZoomChange(Number(e.target.value) / 100)}
          className="h-8 rounded-lg border border-border-subtle bg-surface px-2 text-xs font-medium text-text-main focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue"
        >
          <option value="50">50%</option>
          <option value="75">75%</option>
          <option value="90">90%</option>
          <option value="100">100%</option>
          <option value="125">125%</option>
          <option value="150">150%</option>
          {![50, 75, 90, 100, 125, 150].includes(Math.round(zoom * 100)) ? (
            <option value={String(Math.round(zoom * 100))}>{Math.round(zoom * 100)}%</option>
          ) : null}
        </select>
        <Button
          type="button"
          variant="outline"
          size="sm"
          title="Zoom in"
          onClick={() => onZoomChange(Math.min(1.8, Math.round((zoom + 0.1) * 100) / 100))}
          className="h-8 px-2"
        >
          <ZoomIn className="h-3.5 w-3.5" />
        </Button>

        <Button
          type="button"
          variant={showGuides ? "primary" : "outline"}
          size="sm"
          title="Toggle safe margin & center guides"
          onClick={onToggleGuides}
          className="h-8 px-2.5 text-xs"
        >
          <Grid className="h-3.5 w-3.5" />
          Guides
        </Button>

        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onOpenHistory}
          className="h-8 px-2.5 text-xs"
        >
          <History className="h-3.5 w-3.5" />
          Versions
        </Button>

        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={onToggleJsonModal}
          className="h-8 px-2.5 text-xs"
        >
          <Code2 className="h-3.5 w-3.5" />
          JSON
        </Button>

        <Button
          type="button"
          variant="outline"
          size="sm"
          disabled={busyAction}
          onClick={onOpenPreview}
          className="h-8 px-2.5 text-xs"
        >
          <Eye className="h-3.5 w-3.5" />
          Preview
        </Button>

        {canWrite && status === "ARCHIVED" ? (
          <Button
            type="button"
            variant="primary"
            size="sm"
            disabled={busyAction}
            onClick={onDuplicate}
            className="h-8 px-3 text-xs"
          >
            <Copy className="h-3.5 w-3.5" />
            Duplicate to Edit
          </Button>
        ) : null}

        {canEdit ? (
          <Button
            type="button"
            variant="primary"
            size="sm"
            disabled={busyAction || saveState === "saving"}
            onClick={onSaveDraft}
            className="h-8 px-3 text-xs"
          >
            {saveState === "saving" ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <Save className="h-3.5 w-3.5" />
            )}
            {status === "PUBLISHED" ? `Save as Draft v${currentVersion + 1}` : "Save Draft"}
          </Button>
        ) : null}

        {canPublish && status === "DRAFT" ? (
          <Button
            type="button"
            variant="accent"
            size="sm"
            disabled={busyAction}
            onClick={onPublish}
            className="h-8 px-3 text-xs"
          >
            <CheckCircle2 className="h-3.5 w-3.5" />
            Publish v{currentVersion}
          </Button>
        ) : null}
      </div>
    </div>
  );
}

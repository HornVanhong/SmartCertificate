"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { History, RotateCcw, X } from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { ContextualFormatBar } from "@/components/studio/contextual-format-bar";
import { EditorLeftPanel } from "@/components/studio/editor-left-panel";
import { EditorPropertiesPanel } from "@/components/studio/editor-properties-panel";
import { EditorToolbar, type SaveIndicatorState } from "@/components/studio/editor-toolbar";
import { FabricCanvasStage } from "@/components/studio/fabric-canvas-stage";
import { CertificatePreviewCanvas } from "@/components/templates/certificate-preview-canvas";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { StatePanel } from "@/components/ui/state-panel";
import { listOrganizationAssets } from "@/lib/api/organizations";
import {
  duplicateTemplate,
  getTemplate,
  getTemplateVersion,
  previewTemplate,
  publishTemplate,
  updateTemplateDraft,
} from "@/lib/api/templates";
import {
  ApiClientError,
  type DesignDocument,
  type DesignElement,
  type DesignElementType,
  type DesignPage,
  type MediaAsset,
  type TemplateDetail,
  type TemplatePreview,
} from "@/lib/api/types";
import {
  alignElementToPage,
  createDefaultElement,
  duplicateElement,
  normalizeLayerOrder,
  reorderElementLayer,
  sanitizeDesignForValidation,
  type CreateElementOptions,
  type PageAlignment,
} from "@/lib/editor/design-helpers";
import {
  clearLocalDraftBackup,
  createHistoryState,
  loadLocalDraftBackup,
  pushHistorySnapshot,
  redoHistory,
  saveLocalDraftBackup,
  undoHistory,
  type DesignHistoryState,
  type LocalDraftBackup,
} from "@/lib/editor/design-history";
import type { CertificatePreset } from "@/lib/editor/design-presets";

const PREVIEW_SAMPLES: Record<"en" | "km", Record<string, string>> = {
  en: {
    recipientName: "Alex Rivera",
    courseName: "Enterprise Cloud Architecture",
    issuedDate: "November 12, 2026",
    certificateNumber: "CERT-2026-000184",
  },
  km: {
    recipientName: "សុខ ដារ៉ា (Sok Dara)",
    courseName: "វិញ្ញាបនបត្របញ្ចប់វគ្គសិក្សា",
    issuedDate: "១២ វិច្ឆិកា ២០២៦",
    certificateNumber: "CERT-2026-000185",
  },
};

export function StudioEditorWorkspace({ templateId }: { templateId: string }) {
  const router = useRouter();
  const { hasPermission } = useAuth();
  const canRead = hasPermission("template:read");
  const canWrite = hasPermission("template:write");
  const canPublish = hasPermission("template:publish");

  const [detail, setDetail] = React.useState<TemplateDetail | null>(null);
  const [history, setHistory] = React.useState<DesignHistoryState | null>(null);
  const [assetUrls, setAssetUrls] = React.useState<Record<string, string>>({});
  const [assets, setAssets] = React.useState<MediaAsset[]>([]);
  const [assetsLoading, setAssetsLoading] = React.useState(true);

  const [meta, setMeta] = React.useState({
    name: "",
    category: "",
    description: "",
  });

  const [selectedElementId, setSelectedElementId] = React.useState<string | null>(null);
  const [zoom, setZoom] = React.useState(0.85);
  const [showGuides, setShowGuides] = React.useState(true);
  const [saveState, setSaveState] = React.useState<SaveIndicatorState>("saved");
  const [pendingBackup, setPendingBackup] = React.useState<LocalDraftBackup | null>(null);

  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [feedback, setFeedback] = React.useState<{
    type: "success" | "error";
    text: string;
    fieldErrors?: Record<string, string>;
  } | null>(null);
  const [busyAction, setBusyAction] = React.useState(false);

  const [previewModal, setPreviewModal] = React.useState<{
    sampleMode: "en" | "km";
    loading: boolean;
    data: TemplatePreview | null;
    error: string | null;
  } | null>(null);

  const [showHistoryModal, setShowHistoryModal] = React.useState(false);
  const [jsonModalText, setJsonModalText] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!canRead) return;
    const controller = new AbortController();
    let active = true;

    Promise.all([
      getTemplate(templateId, controller.signal),
      listOrganizationAssets(undefined, controller.signal).catch(() => null),
    ])
      .then(([templateRes, assetsRes]) => {
        if (!active) return;
        const tpl = templateRes.data;
        setDetail(tpl);
        setHistory(createHistoryState(tpl.designJson));
        setMeta({
          name: tpl.name,
          category: tpl.category ?? "",
          description: tpl.description ?? "",
        });

        const mergedUrls: Record<string, string> = { ...tpl.assetUrls };
        if (assetsRes?.data) {
          setAssets(assetsRes.data);
          for (const asset of assetsRes.data) {
            if (asset.signedUrl) {
              mergedUrls[asset.id] = asset.signedUrl;
            }
          }
        }
        setAssetUrls(mergedUrls);
        setAssetsLoading(false);

        const backup = loadLocalDraftBackup(tpl.id);
        if (
          backup &&
          backup.baseVersion === tpl.currentVersion &&
          tpl.status !== "ARCHIVED" &&
          JSON.stringify(backup.designJson) !== JSON.stringify(tpl.designJson)
        ) {
          setPendingBackup(backup);
        }

        setSaveState("saved");
        setError(null);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (!active || (err instanceof DOMException && err.name === "AbortError")) {
          return;
        }
        setError(err instanceof ApiClientError ? err.message : "Failed to load studio template.");
        setLoading(false);
        setAssetsLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [canRead, templateId]);

  const canEdit = Boolean(detail && canWrite && detail.status !== "ARCHIVED");

  const persistDraft = React.useCallback(
    async (isAutosave: boolean) => {
      if (!detail || !history || !canEdit) return;
      const sanitized = sanitizeDesignForValidation(history.present);
      setSaveState("saving");
      setFeedback(null);

      try {
        const wasPublished = detail.status === "PUBLISHED";
        const res = await updateTemplateDraft(detail.id, {
          name: meta.name.trim() || detail.name,
          description: meta.description || undefined,
          category: meta.category || undefined,
          orientation: sanitized.page.orientation,
          pageWidth: sanitized.page.width,
          pageHeight: sanitized.page.height,
          designJson: sanitized,
        });
        setDetail(res.data);
        setAssetUrls((prev) => ({ ...prev, ...res.data.assetUrls }));
        clearLocalDraftBackup(detail.id);
        setPendingBackup(null);
        setSaveState(isAutosave ? "autosaved" : "saved");

        if (wasPublished) {
          setFeedback({
            type: "success",
            text: `Saved as new draft v${res.data.currentVersion} while keeping live v${res.data.publishedVersion} immutable.`,
          });
        }
      } catch (err) {
        setSaveState("error");
        if (err instanceof ApiClientError) {
          setFeedback({
            type: "error",
            text: err.message,
            fieldErrors: err.fieldErrors,
          });
        } else {
          setFeedback({ type: "error", text: "Failed to save template draft." });
        }
      }
    },
    [detail, history, canEdit, meta]
  );

  // Local backup + debounced autosave (only auto-persists to backend when status === "DRAFT")
  React.useEffect(() => {
    if (!detail || !history || !canEdit || saveState !== "dirty") {
      return;
    }

    saveLocalDraftBackup({
      templateId: detail.id,
      baseVersion: detail.currentVersion,
      savedAt: new Date().toISOString(),
      designJson: history.present,
    });

    if (detail.status !== "DRAFT") {
      return;
    }

    const timer = window.setTimeout(() => {
      void persistDraft(true);
    }, 2500);

    return () => window.clearTimeout(timer);
  }, [detail, history, canEdit, saveState, persistDraft]);

  if (!canRead) {
    return (
      <StatePanel
        variant="error"
        title="Template access restricted"
        description="Your current role does not include template:read permission."
      />
    );
  }

  if (loading) {
    return (
      <StatePanel
        variant="loading"
        title="Loading Certificate Studio"
        description="Preparing Fabric.js canvas, fonts, and organization branding assets."
      />
    );
  }

  if (error || !detail || !history) {
    return (
      <StatePanel
        variant="error"
        title="Unable to open studio template"
        description={error || "Template was not found."}
        actionLabel="Back to Gallery"
        onAction={() => router.push("/studio/templates")}
      />
    );
  }

  const design = history.present;
  const selectedElement = design.elements.find((el) => el.id === selectedElementId) ?? null;

  const commitDesign = (updater: (current: DesignDocument) => DesignDocument) => {
    if (!canEdit) return;
    setHistory((prev) => (prev ? pushHistorySnapshot(prev, updater(prev.present)) : prev));
    setSaveState("dirty");
  };

  const handleUndo = () => {
    if (!canEdit) return;
    setHistory((prev) => (prev ? undoHistory(prev) : prev));
    setSaveState("dirty");
  };

  const handleRedo = () => {
    if (!canEdit) return;
    setHistory((prev) => (prev ? redoHistory(prev) : prev));
    setSaveState("dirty");
  };

  const handleAddElement = (type: DesignElementType, options?: CreateElementOptions) => {
    const newElement = createDefaultElement(type, design.page, design.elements.length, options);
    commitDesign((current) => ({
      ...current,
      elements: normalizeLayerOrder([...current.elements, newElement]),
    }));
    setSelectedElementId(newElement.id);
  };

  const handleInsertPlaceholderToken = (token: string, label: string) => {
    if (selectedElement && selectedElement.type === "TEXT" && !selectedElement.locked) {
      commitDesign((current) => ({
        ...current,
        elements: current.elements.map((el) =>
          el.id === selectedElement.id
            ? { ...el, text: `${el.text ?? ""}${el.text ? " " : ""}${token}` }
            : el
        ),
      }));
      return;
    }

    handleAddElement("TEXT", {
      name: label,
      text: token,
      fontSize: 18,
      fontFamily: "Inter",
      height: 36,
    });
  };

  const handleInsertAsset = (asset: MediaAsset) => {
    const targetType: DesignElementType =
      asset.assetType === "LOGO"
        ? "LOGO"
        : asset.assetType === "SIGNATURE"
          ? "SIGNATURE"
          : asset.assetType === "STAMP"
            ? "STAMP"
            : "IMAGE";

    if (asset.signedUrl) {
      setAssetUrls((prev) => ({ ...prev, [asset.id]: asset.signedUrl! }));
    }

    handleAddElement(targetType, {
      name: asset.originalFileName,
      assetId: asset.id,
      width: asset.width ?? undefined,
      height: asset.height ?? undefined,
    });
  };

  const handleSetBackgroundAsset = (asset: MediaAsset) => {
    if (asset.signedUrl) {
      setAssetUrls((prev) => ({ ...prev, [asset.id]: asset.signedUrl! }));
    }
    commitDesign((current) => ({
      ...current,
      page: {
        ...current.page,
        backgroundAssetId: asset.id,
      },
    }));
  };

  const handleUpdatePage = (patch: Partial<DesignPage>) => {
    commitDesign((current) => ({
      ...current,
      page: { ...current.page, ...patch },
    }));
  };

  const handleApplyPreset = (preset: CertificatePreset) => {
    const nextDoc = preset.build(design.page.orientation);
    commitDesign(() => nextDoc);
    setSelectedElementId(null);
    setFeedback({
      type: "success",
      text: `Applied "${preset.name}" starter design to canvas.`,
    });
  };

  const handleUpdateElement = (elementId: string, patch: Partial<DesignElement>) => {
    commitDesign((current) => ({
      ...current,
      elements: current.elements.map((el) => (el.id === elementId ? { ...el, ...patch } : el)),
    }));
  };

  const handleCommitCanvasElement = (updatedElement: DesignElement) => {
    commitDesign((current) => ({
      ...current,
      elements: current.elements.map((el) => (el.id === updatedElement.id ? updatedElement : el)),
    }));
  };

  const handleDuplicateElement = (elementId: string) => {
    const source = design.elements.find((el) => el.id === elementId);
    if (!source) return;
    const copy = duplicateElement(source, design.elements.length);
    commitDesign((current) => ({
      ...current,
      elements: normalizeLayerOrder([...current.elements, copy]),
    }));
    setSelectedElementId(copy.id);
  };

  const handleDeleteElement = (elementId: string) => {
    commitDesign((current) => ({
      ...current,
      elements: normalizeLayerOrder(current.elements.filter((el) => el.id !== elementId)),
    }));
    if (selectedElementId === elementId) {
      setSelectedElementId(null);
    }
  };

  const handleReorderLayer = (elementId: string, direction: "up" | "down") => {
    commitDesign((current) => ({
      ...current,
      elements: reorderElementLayer(current.elements, elementId, direction),
    }));
  };

  const handleAlignElement = (elementId: string, alignment: PageAlignment) => {
    commitDesign((current) => ({
      ...current,
      elements: current.elements.map((el) =>
        el.id === elementId ? alignElementToPage(el, current.page, alignment) : el
      ),
    }));
  };

  const runPreview = async (sampleMode: "en" | "km") => {
    setPreviewModal({ sampleMode, loading: true, data: null, error: null });
    try {
      const sanitized = sanitizeDesignForValidation(history.present);
      const res = await previewTemplate(detail.id, {
        designJson: sanitized,
        sampleData: PREVIEW_SAMPLES[sampleMode],
      });
      setPreviewModal({ sampleMode, loading: false, data: res.data, error: null });
    } catch (err) {
      setPreviewModal({
        sampleMode,
        loading: false,
        data: null,
        error: err instanceof ApiClientError ? err.message : "Failed to run server preview.",
      });
    }
  };

  const handlePublish = async () => {
    if (saveState === "dirty") {
      await persistDraft(false);
    }
    setBusyAction(true);
    setFeedback(null);
    try {
      const res = await publishTemplate(detail.id);
      setDetail(res.data);
      setFeedback({
        type: "success",
        text: `Published immutable template version v${res.data.publishedVersion}. Ready for issuance.`,
      });
    } catch (err) {
      setFeedback({
        type: "error",
        text: err instanceof ApiClientError ? err.message : "Failed to publish template.",
      });
    } finally {
      setBusyAction(false);
    }
  };

  const handleDuplicateTemplate = async () => {
    setBusyAction(true);
    setFeedback(null);
    try {
      const res = await duplicateTemplate(detail.id);
      router.push(`/studio/templates/${res.data.id}`);
    } catch (err) {
      setFeedback({
        type: "error",
        text: err instanceof ApiClientError ? err.message : "Failed to duplicate template.",
      });
      setBusyAction(false);
    }
  };

  const handleRestoreVersion = async (versionNumber: number) => {
    setBusyAction(true);
    try {
      const res = await getTemplateVersion(detail.id, versionNumber);
      commitDesign(() => res.data.designJson);
      setShowHistoryModal(false);
      setFeedback({
        type: "success",
        text: `Loaded design snapshot from version v${versionNumber} into editor.`,
      });
    } catch (err) {
      setFeedback({
        type: "error",
        text: err instanceof ApiClientError ? err.message : "Failed to load template version.",
      });
    } finally {
      setBusyAction(false);
    }
  };

  return (
    <div className="flex flex-col gap-3">
      <EditorToolbar
        templateName={meta.name}
        onNameChange={(name) => {
          setMeta((prev) => ({ ...prev, name }));
          setSaveState("dirty");
        }}
        status={detail.status}
        currentVersion={detail.currentVersion}
        publishedVersion={detail.publishedVersion}
        canEdit={canEdit}
        canWrite={canWrite}
        canPublish={canPublish}
        saveState={saveState}
        canUndo={history.past.length > 0}
        canRedo={history.future.length > 0}
        zoom={zoom}
        showGuides={showGuides}
        busyAction={busyAction}
        onUndo={handleUndo}
        onRedo={handleRedo}
        onZoomChange={setZoom}
        onToggleGuides={() => setShowGuides((v) => !v)}
        onOpenPreview={() => void runPreview("en")}
        onOpenHistory={() => setShowHistoryModal(true)}
        onToggleJsonModal={() =>
          setJsonModalText(JSON.stringify(sanitizeDesignForValidation(design), null, 2))
        }
        onSaveDraft={() => void persistDraft(false)}
        onPublish={() => void handlePublish()}
        onDuplicate={() => void handleDuplicateTemplate()}
      />

      {detail.status === "ARCHIVED" ? (
        <div className="rounded-lg border border-border-subtle bg-slate-100 px-4 py-2 text-xs text-text-secondary">
          This template is archived and read-only. Use <strong>Duplicate to Edit</strong> to create
          a new editable draft.
        </div>
      ) : detail.status === "PUBLISHED" ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-2 text-xs text-amber-900">
          Published version <strong>v{detail.publishedVersion}</strong> is immutable. Making edits
          keeps changes locally backed up until you click{" "}
          <strong>Save as Draft v{detail.currentVersion + 1}</strong>.
        </div>
      ) : null}

      {pendingBackup ? (
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-brand-blue/30 bg-brand-blue/5 px-4 py-2 text-xs text-brand-navy">
          <span>
            Unsaved local draft backup found from {new Date(pendingBackup.savedAt).toLocaleString()}
            .
          </span>
          <div className="flex items-center gap-2">
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={() => {
                clearLocalDraftBackup(detail.id);
                setPendingBackup(null);
              }}
              className="h-7 px-2 text-xs"
            >
              Discard
            </Button>
            <Button
              type="button"
              size="sm"
              variant="primary"
              onClick={() => {
                commitDesign(() => pendingBackup.designJson);
                setPendingBackup(null);
              }}
              className="h-7 px-2 text-xs"
            >
              <RotateCcw className="h-3 w-3" />
              Restore Backup
            </Button>
          </div>
        </div>
      ) : null}

      {feedback ? (
        <div
          className={`flex items-start justify-between gap-3 rounded-lg border px-4 py-2.5 text-xs ${
            feedback.type === "success"
              ? "border-emerald-200 bg-emerald-50 text-emerald-800"
              : "border-rose-200 bg-rose-50 text-rose-700"
          }`}
        >
          <div>
            <p className="font-medium">{feedback.text}</p>
            {feedback.fieldErrors && Object.keys(feedback.fieldErrors).length > 0 ? (
              <ul className="mt-1 list-disc pl-4">
                {Object.entries(feedback.fieldErrors).map(([field, msg]) => (
                  <li key={field}>
                    <code className="font-mono">{field}</code>: {msg}
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
          <button
            type="button"
            aria-label="Dismiss notification"
            onClick={() => setFeedback(null)}
            className="rounded p-0.5 hover:bg-black/5"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      ) : null}

      <div className="grid min-h-[680px] gap-3 lg:h-[calc(100vh-13rem)] lg:grid-cols-[316px_minmax(0,1fr)_280px]">
        <EditorLeftPanel
          canEdit={canEdit}
          page={design.page}
          elements={design.elements}
          selectedElementId={selectedElementId}
          assets={assets}
          assetsLoading={assetsLoading}
          onApplyPreset={handleApplyPreset}
          onUpdatePage={handleUpdatePage}
          onAddElement={handleAddElement}
          onInsertPlaceholderToken={handleInsertPlaceholderToken}
          onInsertAsset={handleInsertAsset}
          onSetBackgroundAsset={handleSetBackgroundAsset}
          onSelectElement={setSelectedElementId}
          onToggleVisibility={(id) => {
            const el = design.elements.find((item) => item.id === id);
            if (el) handleUpdateElement(id, { visible: !el.visible });
          }}
          onToggleLock={(id) => {
            const el = design.elements.find((item) => item.id === id);
            if (el) handleUpdateElement(id, { locked: !el.locked });
          }}
          onReorderLayer={handleReorderLayer}
          onDuplicateElement={handleDuplicateElement}
          onDeleteElement={handleDeleteElement}
        />

        <div className="flex min-w-0 flex-1 flex-col gap-2.5 overflow-hidden">
          <ContextualFormatBar
            canEdit={canEdit}
            page={design.page}
            selectedElement={selectedElement}
            onUpdateElement={handleUpdateElement}
            onAlignElement={handleAlignElement}
            onReorderLayer={handleReorderLayer}
            onDuplicateElement={handleDuplicateElement}
            onDeleteElement={handleDeleteElement}
          />
          <FabricCanvasStage
            design={design}
            assetUrls={assetUrls}
            editable={canEdit}
            selectedElementId={selectedElementId}
            zoom={zoom}
            showGuides={showGuides}
            onSelectElement={setSelectedElementId}
            onCommitElementChange={handleCommitCanvasElement}
            onDeleteSelected={() => {
              if (selectedElementId) handleDeleteElement(selectedElementId);
            }}
            onDuplicateSelected={() => {
              if (selectedElementId) handleDuplicateElement(selectedElementId);
            }}
            onUndo={handleUndo}
            onRedo={handleRedo}
            onSaveShortcut={() => void persistDraft(false)}
          />
        </div>

        <EditorPropertiesPanel
          canEdit={canEdit}
          page={design.page}
          selectedElement={selectedElement}
          assets={assets}
          category={meta.category}
          description={meta.description}
          onMetaChange={(field, value) => {
            setMeta((prev) => ({ ...prev, [field]: value }));
            setSaveState("dirty");
          }}
          onUpdatePage={handleUpdatePage}
          onUpdateElement={handleUpdateElement}
          onAlignElement={handleAlignElement}
          onDuplicateElement={handleDuplicateElement}
          onDeleteElement={handleDeleteElement}
        />
      </div>

      {previewModal ? (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4"
        >
          <div className="max-h-[92vh] w-full max-w-4xl overflow-y-auto rounded-xl border border-border-subtle bg-surface p-6 shadow-xl">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-border-subtle pb-3">
              <div>
                <h2 className="text-lg font-semibold text-text-main">
                  Server Preview: {meta.name}
                </h2>
                <p className="text-xs text-text-secondary">
                  Validated against canonical backend schema with sample placeholders
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Button
                  size="sm"
                  variant={previewModal.sampleMode === "en" ? "primary" : "outline"}
                  onClick={() => void runPreview("en")}
                >
                  English Sample
                </Button>
                <Button
                  size="sm"
                  variant={previewModal.sampleMode === "km" ? "primary" : "outline"}
                  onClick={() => void runPreview("km")}
                >
                  Khmer Sample
                </Button>
                <Button
                  size="sm"
                  variant="ghost"
                  aria-label="Close preview modal"
                  onClick={() => setPreviewModal(null)}
                >
                  <X className="h-4 w-4" />
                </Button>
              </div>
            </div>

            {previewModal.loading ? (
              <StatePanel
                variant="loading"
                title="Validating & rendering preview"
                description="Checking design rules and resolving signed organization assets."
              />
            ) : previewModal.error ? (
              <StatePanel
                variant="error"
                title="Preview validation failed"
                description={previewModal.error}
              />
            ) : previewModal.data ? (
              <div className="space-y-4">
                {previewModal.data.warnings.length > 0 ? (
                  <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800">
                    <p className="font-semibold">Validation Warnings</p>
                    <ul className="mt-1 list-disc pl-4">
                      {previewModal.data.warnings.map((w) => (
                        <li key={w}>{w}</li>
                      ))}
                    </ul>
                  </div>
                ) : null}
                <div className="rounded-lg bg-slate-100 p-4">
                  <CertificatePreviewCanvas
                    design={previewModal.data.resolvedDesignJson}
                    assetUrls={previewModal.data.assetUrls}
                    resolvedPlaceholders={previewModal.data.resolvedPlaceholders}
                    className="mx-auto max-w-2xl"
                  />
                </div>
              </div>
            ) : null}
          </div>
        </div>
      ) : null}

      {showHistoryModal ? (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4"
        >
          <div className="max-h-[85vh] w-full max-w-lg overflow-y-auto rounded-xl border border-border-subtle bg-surface p-5 shadow-xl">
            <div className="mb-4 flex items-center justify-between border-b border-border-subtle pb-3">
              <div className="flex items-center gap-2">
                <History className="h-4 w-4 text-brand-navy" />
                <h2 className="text-base font-semibold text-text-main">Version History</h2>
              </div>
              <Button
                size="sm"
                variant="ghost"
                aria-label="Close history modal"
                onClick={() => setShowHistoryModal(false)}
              >
                <X className="h-4 w-4" />
              </Button>
            </div>
            <div className="space-y-2">
              {detail.versions.map((ver) => (
                <div
                  key={ver.id}
                  className="flex items-center justify-between rounded-lg border border-border-subtle px-3 py-2.5 text-xs"
                >
                  <div>
                    <div className="flex items-center gap-1.5 font-medium text-text-main">
                      <span>Version {ver.versionNumber}</span>
                      {detail.publishedVersion === ver.versionNumber ? (
                        <Badge variant="gold">Published</Badge>
                      ) : null}
                      {detail.currentVersion === ver.versionNumber ? (
                        <Badge variant="navy">Current</Badge>
                      ) : null}
                    </div>
                    <p className="mt-0.5 text-[11px] text-text-secondary">
                      {new Date(ver.createdAt).toLocaleString()}
                    </p>
                  </div>
                  {canEdit ? (
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      disabled={busyAction}
                      onClick={() => void handleRestoreVersion(ver.versionNumber)}
                      className="h-7 px-2.5 text-xs"
                    >
                      Load into Canvas
                    </Button>
                  ) : null}
                </div>
              ))}
            </div>
          </div>
        </div>
      ) : null}

      {jsonModalText !== null ? (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4"
        >
          <div className="flex max-h-[90vh] w-full max-w-3xl flex-col rounded-xl border border-border-subtle bg-surface p-5 shadow-xl">
            <div className="mb-3 flex items-center justify-between border-b border-border-subtle pb-3">
              <div>
                <h2 className="text-base font-semibold text-text-main">
                  Canonical Design JSON (Schema v1.0)
                </h2>
                <p className="text-xs text-text-secondary">
                  Synced bidirectionally with the Fabric.js canvas.
                </p>
              </div>
              <Button
                size="sm"
                variant="ghost"
                aria-label="Close JSON modal"
                onClick={() => setJsonModalText(null)}
              >
                <X className="h-4 w-4" />
              </Button>
            </div>
            <textarea
              rows={20}
              readOnly={!canEdit}
              spellCheck={false}
              value={jsonModalText}
              onChange={(e) => setJsonModalText(e.target.value)}
              className="w-full flex-1 rounded-lg border border-border-subtle bg-slate-950 p-3 font-mono text-xs text-slate-100"
            />
            {canEdit ? (
              <div className="mt-3 flex justify-end gap-2">
                <Button variant="outline" size="sm" onClick={() => setJsonModalText(null)}>
                  Close
                </Button>
                <Button
                  size="sm"
                  onClick={() => {
                    try {
                      const parsed = JSON.parse(jsonModalText) as DesignDocument;
                      if (!parsed?.page || !Array.isArray(parsed?.elements)) {
                        throw new Error("Invalid canonical DesignDocument structure.");
                      }
                      commitDesign(() => parsed);
                      setJsonModalText(null);
                    } catch {
                      setFeedback({
                        type: "error",
                        text: "Cannot apply JSON: invalid DesignDocument syntax or structure.",
                      });
                    }
                  }}
                >
                  Apply JSON to Canvas
                </Button>
              </div>
            ) : null}
          </div>
        </div>
      ) : null}
    </div>
  );
}

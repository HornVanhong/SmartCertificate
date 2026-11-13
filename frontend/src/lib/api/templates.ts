import { apiRequest } from "./client";
import type {
  DesignDocument,
  TemplateDetail,
  TemplateOrientation,
  TemplatePreview,
  TemplateStatus,
  TemplateSummary,
  TemplateVersion,
} from "./types";

export interface ListTemplatesParams {
  q?: string;
  status?: TemplateStatus | "";
  category?: string;
  orientation?: TemplateOrientation | "";
  page?: number;
  size?: number;
}

export interface CreateTemplatePayload {
  name: string;
  description?: string;
  category?: string;
  orientation: TemplateOrientation;
  pageWidth?: number;
  pageHeight?: number;
  designJson?: DesignDocument;
}

export interface UpdateTemplateDraftPayload {
  name: string;
  description?: string;
  category?: string;
  orientation: TemplateOrientation;
  pageWidth: number;
  pageHeight: number;
  designJson: DesignDocument;
}

export interface PreviewTemplatePayload {
  designJson?: DesignDocument;
  sampleData?: Record<string, string>;
}

export function listTemplates(params: ListTemplatesParams = {}, signal?: AbortSignal) {
  const search = new URLSearchParams();
  if (params.q?.trim()) search.set("q", params.q.trim());
  if (params.status) search.set("status", params.status);
  if (params.category?.trim()) search.set("category", params.category.trim());
  if (params.orientation) search.set("orientation", params.orientation);
  search.set("page", String(params.page ?? 0));
  search.set("size", String(params.size ?? 12));

  return apiRequest<TemplateSummary[]>(`/templates?${search.toString()}`, {
    method: "GET",
    cache: "no-store",
    signal,
  });
}

export function createTemplate(payload: CreateTemplatePayload) {
  return apiRequest<TemplateDetail>("/templates", {
    method: "POST",
    body: payload,
  });
}

export function getTemplate(templateId: string, signal?: AbortSignal) {
  return apiRequest<TemplateDetail>(`/templates/${encodeURIComponent(templateId)}`, {
    method: "GET",
    cache: "no-store",
    signal,
  });
}

export function getTemplateVersion(
  templateId: string,
  versionNumber: number,
  signal?: AbortSignal
) {
  return apiRequest<TemplateVersion>(
    `/templates/${encodeURIComponent(templateId)}/versions/${versionNumber}`,
    {
      method: "GET",
      cache: "no-store",
      signal,
    }
  );
}

export function updateTemplateDraft(templateId: string, payload: UpdateTemplateDraftPayload) {
  return apiRequest<TemplateDetail>(`/templates/${encodeURIComponent(templateId)}/draft`, {
    method: "PUT",
    body: payload,
  });
}

export function duplicateTemplate(templateId: string, name?: string) {
  return apiRequest<TemplateDetail>(`/templates/${encodeURIComponent(templateId)}/duplicate`, {
    method: "POST",
    body: name?.trim() ? { name: name.trim() } : {},
  });
}

export function previewTemplate(templateId: string, payload: PreviewTemplatePayload = {}) {
  return apiRequest<TemplatePreview>(`/templates/${encodeURIComponent(templateId)}/preview`, {
    method: "POST",
    body: payload,
  });
}

export function publishTemplate(templateId: string) {
  return apiRequest<TemplateDetail>(`/templates/${encodeURIComponent(templateId)}/publish`, {
    method: "POST",
  });
}

export function archiveTemplate(templateId: string) {
  return apiRequest<TemplateDetail>(`/templates/${encodeURIComponent(templateId)}/archive`, {
    method: "POST",
  });
}

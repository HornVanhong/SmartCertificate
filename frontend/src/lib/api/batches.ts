import { apiBlobRequest, apiRequest } from "./client";
import type { BatchDetail, BatchErrorItem, BatchSummary } from "./types";

export function listBatches(params: { page?: number; size?: number } = {}, signal?: AbortSignal) {
  const search = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? 10),
  });
  return apiRequest<BatchSummary[]>(`/certificate-batches?${search.toString()}`, {
    method: "GET",
    cache: "no-store",
    signal,
  });
}

export function importBatchFile(params: {
  templateId: string;
  file: File;
  idempotencyKey?: string;
}) {
  const formData = new FormData();
  formData.append("file", params.file);

  const search = new URLSearchParams({ templateId: params.templateId });
  if (params.idempotencyKey?.trim()) {
    search.set("idempotencyKey", params.idempotencyKey.trim());
  }

  return apiRequest<BatchDetail>(`/certificate-batches/import?${search.toString()}`, {
    method: "POST",
    body: formData,
  });
}

export function getBatch(batchId: string, signal?: AbortSignal) {
  return apiRequest<BatchDetail>(`/certificate-batches/${encodeURIComponent(batchId)}`, {
    method: "GET",
    cache: "no-store",
    signal,
  });
}

export function saveBatchMapping(batchId: string, mappings: Record<string, string>) {
  return apiRequest<BatchDetail>(`/certificate-batches/${encodeURIComponent(batchId)}/mapping`, {
    method: "POST",
    body: { mappings },
  });
}

export function validateBatch(batchId: string) {
  return apiRequest<BatchDetail>(`/certificate-batches/${encodeURIComponent(batchId)}/validate`, {
    method: "POST",
  });
}

export function generateBatch(batchId: string) {
  return apiRequest<BatchDetail>(`/certificate-batches/${encodeURIComponent(batchId)}/generate`, {
    method: "POST",
  });
}

export function retryBatch(batchId: string) {
  return apiRequest<BatchDetail>(`/certificate-batches/${encodeURIComponent(batchId)}/retry`, {
    method: "POST",
  });
}

export function listBatchErrors(
  batchId: string,
  params: { page?: number; size?: number } = {},
  signal?: AbortSignal
) {
  const search = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? 20),
  });
  return apiRequest<BatchErrorItem[]>(
    `/certificate-batches/${encodeURIComponent(batchId)}/errors?${search.toString()}`,
    {
      method: "GET",
      cache: "no-store",
      signal,
    }
  );
}

export async function downloadBatchZipBlob(batchId: string, signal?: AbortSignal): Promise<Blob> {
  const { blob } = await apiBlobRequest(
    `/certificate-batches/${encodeURIComponent(batchId)}/download`,
    {
      method: "GET",
      cache: "no-store",
      signal,
    }
  );
  return blob;
}

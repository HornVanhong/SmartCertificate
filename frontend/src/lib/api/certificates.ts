import { apiBlobRequest, apiRequest } from "./client";
import type {
  CertificateAuditLog,
  CertificateDetail,
  CertificateFormat,
  CertificateStatus,
  CertificateSummary,
} from "./types";

export interface ListCertificatesParams {
  q?: string;
  status?: CertificateStatus | "";
  templateId?: string;
  page?: number;
  size?: number;
}

export interface IssueCertificatePayload {
  templateId: string;
  recipientName: string;
  recipientEmail?: string;
  courseName: string;
  completionDate?: string;
  expiresAt?: string;
  metadata?: Record<string, string>;
  idempotencyKey?: string;
}

export function listCertificates(params: ListCertificatesParams = {}, signal?: AbortSignal) {
  const search = new URLSearchParams();
  if (params.q?.trim()) search.set("q", params.q.trim());
  if (params.status) search.set("status", params.status);
  if (params.templateId?.trim()) search.set("templateId", params.templateId.trim());
  search.set("page", String(params.page ?? 0));
  search.set("size", String(params.size ?? 20));

  return apiRequest<CertificateSummary[]>(`/certificates?${search.toString()}`, {
    method: "GET",
    cache: "no-store",
    signal,
  });
}

export function issueCertificate(payload: IssueCertificatePayload) {
  return apiRequest<CertificateDetail>("/certificates", {
    method: "POST",
    body: payload,
  });
}

export function getCertificate(certificateId: string, signal?: AbortSignal) {
  return apiRequest<CertificateDetail>(`/certificates/${encodeURIComponent(certificateId)}`, {
    method: "GET",
    cache: "no-store",
    signal,
  });
}

export async function downloadCertificateBlob(
  certificateId: string,
  format: CertificateFormat,
  inline = false,
  signal?: AbortSignal
): Promise<Blob> {
  const search = new URLSearchParams({
    format,
    inline: String(inline),
  });
  const { blob } = await apiBlobRequest(
    `/certificates/${encodeURIComponent(certificateId)}/download?${search.toString()}`,
    {
      method: "GET",
      cache: "no-store",
      signal,
    }
  );
  return blob;
}

export function triggerBrowserBlobDownload(blob: Blob, filename: string): void {
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
}

export function revokeCertificate(certificateId: string, reason: string) {
  return apiRequest<CertificateDetail>(
    `/certificates/${encodeURIComponent(certificateId)}/revoke`,
    {
      method: "POST",
      body: { reason: reason.trim() },
    }
  );
}

export function listCertificateAuditLogs(
  certificateId: string,
  params: { page?: number; size?: number } = {},
  signal?: AbortSignal
) {
  const search = new URLSearchParams({
    page: String(params.page ?? 0),
    size: String(params.size ?? 20),
  });
  return apiRequest<CertificateAuditLog[]>(
    `/certificates/${encodeURIComponent(certificateId)}/audit?${search.toString()}`,
    {
      method: "GET",
      cache: "no-store",
      signal,
    }
  );
}

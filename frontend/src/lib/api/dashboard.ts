import { apiRequest } from "./client";
import type { CertificateAuditLog, DashboardSummary, IssuanceTrendPoint } from "./types";

export interface ListAuditLogsParams {
  action?: string;
  q?: string;
  page?: number;
  size?: number;
}

export function fetchDashboardSummary(signal?: AbortSignal) {
  return apiRequest<DashboardSummary>("/dashboard/summary", {
    method: "GET",
    cache: "no-store",
    signal,
  });
}

export function fetchIssuanceTrends(days = 30, signal?: AbortSignal) {
  const safeDays = Math.min(Math.max(days, 7), 90);
  return apiRequest<IssuanceTrendPoint[]>(`/dashboard/issuance-trends?days=${safeDays}`, {
    method: "GET",
    cache: "no-store",
    signal,
  });
}

export function fetchRecentActivity(limit = 10, signal?: AbortSignal) {
  const safeLimit = Math.min(Math.max(limit, 1), 30);
  return apiRequest<CertificateAuditLog[]>(`/dashboard/recent-activity?limit=${safeLimit}`, {
    method: "GET",
    cache: "no-store",
    signal,
  });
}

export function listAuditLogs(params: ListAuditLogsParams = {}, signal?: AbortSignal) {
  const search = new URLSearchParams();
  if (params.action?.trim()) search.set("action", params.action.trim());
  if (params.q?.trim()) search.set("q", params.q.trim());
  search.set("page", String(params.page ?? 0));
  search.set("size", String(params.size ?? 20));

  return apiRequest<CertificateAuditLog[]>(`/audit-logs?${search.toString()}`, {
    method: "GET",
    cache: "no-store",
    signal,
  });
}

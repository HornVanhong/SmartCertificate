import {
  ApiClientError,
  type ApiErrorResponse,
  type ApiResponse,
  type HealthStatus,
} from "./types";

const DEFAULT_API_BASE_URL = "/api/v1";

export function getApiBaseUrl(): string {
  if (typeof window !== "undefined") {
    const configured = process.env.NEXT_PUBLIC_API_BASE_URL;
    if (configured && !configured.includes("localhost:8080")) {
      return configured.replace(/\/+$/, "");
    }
    return "/api/v1";
  }
  return (process.env.NEXT_PUBLIC_API_BASE_URL || DEFAULT_API_BASE_URL).replace(/\/+$/, "");
}

export function resolveBackendAssetUrl(signedPathOrUrl: string | null | undefined): string | null {
  if (!signedPathOrUrl) {
    return null;
  }
  if (signedPathOrUrl.startsWith("http://") || signedPathOrUrl.startsWith("https://")) {
    return signedPathOrUrl;
  }
  const base = getApiBaseUrl();
  const origin = base.startsWith("http") ? base.replace(/\/api\/v1$/, "") : "";
  return `${origin}${signedPathOrUrl.startsWith("/") ? "" : "/"}${signedPathOrUrl}`;
}

function readCookie(name: string): string | null {
  if (typeof document === "undefined") {
    return null;
  }
  const prefix = `${name}=`;
  const parts = document.cookie.split(";");
  for (const part of parts) {
    const trimmed = part.trim();
    if (trimmed.startsWith(prefix)) {
      return decodeURIComponent(trimmed.substring(prefix.length));
    }
  }
  return null;
}

async function ensureCsrfCookie(): Promise<string | null> {
  let token = readCookie("XSRF-TOKEN");
  if (!token) {
    await fetch(`${getApiBaseUrl()}/auth/csrf`, {
      method: "GET",
      credentials: "include",
    }).catch(() => null);
    token = readCookie("XSRF-TOKEN");
  }
  return token;
}

let inMemoryAccessToken: string | null = null;
let inMemoryRefreshToken: string | null = null;

export function setAuthTokens(accessToken: string | null, refreshToken: string | null = null) {
  inMemoryAccessToken = accessToken;
  inMemoryRefreshToken = refreshToken;
  if (typeof window !== "undefined") {
    try {
      if (accessToken) {
        localStorage.setItem("sc_access_token", accessToken);
      } else {
        localStorage.removeItem("sc_access_token");
      }
      if (refreshToken) {
        localStorage.setItem("sc_refresh_token", refreshToken);
      } else {
        localStorage.removeItem("sc_refresh_token");
      }
    } catch {}
  }
}

export function getAccessToken(): string | null {
  if (inMemoryAccessToken) return inMemoryAccessToken;
  if (typeof window !== "undefined") {
    try {
      return localStorage.getItem("sc_access_token");
    } catch {}
  }
  return null;
}

export function getRefreshToken(): string | null {
  if (inMemoryRefreshToken) return inMemoryRefreshToken;
  if (typeof window !== "undefined") {
    try {
      return localStorage.getItem("sc_refresh_token");
    } catch {}
  }
  return null;
}

let refreshPromise: Promise<boolean> | null = null;

async function attemptSessionRefresh(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      try {
        const csrfToken = await ensureCsrfCookie();
        const headers: Record<string, string> = {
          Accept: "application/json",
          "Content-Type": "application/json",
        };
        if (csrfToken) {
          headers["X-XSRF-TOKEN"] = csrfToken;
        }
        const rfToken = getRefreshToken();
        const bodyPayload = rfToken ? { refreshToken: rfToken } : {};
        const res = await fetch(`${getApiBaseUrl()}/auth/refresh`, {
          method: "POST",
          credentials: "include",
          headers,
          body: JSON.stringify(bodyPayload),
        });
        if (res.ok) {
          const data = await res.json().catch(() => null);
          if (data?.data?.accessToken) {
            setAuthTokens(data.data.accessToken, data.data.refreshToken);
          }
          return true;
        }
        return false;
      } catch {
        return false;
      } finally {
        refreshPromise = null;
      }
    })();
  }
  return refreshPromise;
}

interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
  skipAuthRefresh?: boolean;
}

async function performAuthorizedFetch(
  path: string,
  options: RequestOptions = {},
  defaultAccept = "application/json"
): Promise<Response> {
  const { body, headers, skipAuthRefresh = false, ...rest } = options;
  const method = (rest.method ?? "GET").toUpperCase();
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  const url = `${getApiBaseUrl()}${normalizedPath}`;

  const requestHeaders = new Headers(headers);
  if (body !== undefined && !(body instanceof FormData)) {
    requestHeaders.set("Content-Type", "application/json");
  }
  if (!requestHeaders.has("Accept")) {
    requestHeaders.set("Accept", defaultAccept);
  }

  if (method !== "GET" && method !== "HEAD" && method !== "OPTIONS") {
    const csrfToken = await ensureCsrfCookie();
    if (csrfToken) {
      requestHeaders.set("X-XSRF-TOKEN", csrfToken);
    }
  }

  const token = getAccessToken();
  if (token && !requestHeaders.has("Authorization")) {
    requestHeaders.set("Authorization", `Bearer ${token}`);
  }

  const response = await fetch(url, {
    ...rest,
    method,
    headers: requestHeaders,
    body: body === undefined ? undefined : body instanceof FormData ? body : JSON.stringify(body),
    credentials: "include",
  });

  if (
    response.status === 401 &&
    !skipAuthRefresh &&
    !normalizedPath.startsWith("/auth/login") &&
    !normalizedPath.startsWith("/auth/register") &&
    !normalizedPath.startsWith("/auth/refresh")
  ) {
    const refreshed = await attemptSessionRefresh();
    if (refreshed) {
      return performAuthorizedFetch(path, { ...options, skipAuthRefresh: true }, defaultAccept);
    }
  }

  if (!response.ok) {
    const isJson = response.headers.get("content-type")?.includes("application/json");
    const payload = isJson ? await response.json().catch(() => null) : null;
    const errorEnvelope = payload as ApiErrorResponse | null;
    if (errorEnvelope?.error) {
      throw new ApiClientError({
        status: response.status,
        code: errorEnvelope.error.code,
        message: errorEnvelope.error.message,
        fieldErrors: errorEnvelope.error.fieldErrors,
        requestId: errorEnvelope.error.requestId,
      });
    }
    throw new ApiClientError({
      status: response.status,
      code: "HTTP_ERROR",
      message: `Request failed with status ${response.status}`,
    });
  }

  return response;
}

export async function apiRequest<T>(
  path: string,
  options: RequestOptions = {}
): Promise<ApiResponse<T>> {
  const response = await performAuthorizedFetch(path, options, "application/json");
  return (await response.json()) as ApiResponse<T>;
}

export async function apiBlobRequest(
  path: string,
  options: RequestOptions = {}
): Promise<{ blob: Blob; contentDisposition: string | null }> {
  const response = await performAuthorizedFetch(path, options, "*/*");
  return {
    blob: await response.blob(),
    contentDisposition: response.headers.get("content-disposition"),
  };
}

export async function fetchHealthStatus(signal?: AbortSignal): Promise<ApiResponse<HealthStatus>> {
  return apiRequest<HealthStatus>("/health", {
    method: "GET",
    cache: "no-store",
    signal,
    skipAuthRefresh: true,
  });
}

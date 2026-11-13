import { apiRequest, setAuthTokens } from "./client";
import type { AuthMe, AuthSession } from "./types";

export interface RegisterPayload {
  fullName: string;
  email: string;
  password: string;
  organizationName: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export async function registerAccount(payload: RegisterPayload) {
  const response = await apiRequest<AuthSession>("/auth/register", {
    method: "POST",
    body: payload,
  });
  if (response?.data?.accessToken) {
    setAuthTokens(response.data.accessToken, response.data.refreshToken);
  }
  return response;
}

export async function loginAccount(payload: LoginPayload) {
  const response = await apiRequest<AuthSession>("/auth/login", {
    method: "POST",
    body: payload,
  });
  if (response?.data?.accessToken) {
    setAuthTokens(response.data.accessToken, response.data.refreshToken);
  }
  return response;
}

export async function logoutAccount() {
  setAuthTokens(null, null);
  return apiRequest<{ loggedOut: boolean }>("/auth/logout", {
    method: "POST",
    body: {},
    skipAuthRefresh: true,
  });
}

export function fetchCurrentSession(signal?: AbortSignal) {
  return apiRequest<AuthMe>("/auth/me", {
    method: "GET",
    cache: "no-store",
    signal,
  });
}

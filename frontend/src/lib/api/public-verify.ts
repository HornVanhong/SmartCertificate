import { apiRequest } from "./client";
import type { PublicVerificationResult } from "./types";

export function verifyCertificateToken(token: string, signal?: AbortSignal) {
  return apiRequest<PublicVerificationResult>(
    `/public/verify/${encodeURIComponent(token.trim())}`,
    {
      method: "GET",
      cache: "no-store",
      signal,
      skipAuthRefresh: true,
    }
  );
}

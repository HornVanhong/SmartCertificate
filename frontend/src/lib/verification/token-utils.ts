export function extractVerificationToken(rawInput: string): string {
  const trimmed = rawInput.trim();
  if (!trimmed) {
    return "";
  }
  try {
    if (trimmed.includes("://") || trimmed.startsWith("/verify")) {
      const parsed = new URL(trimmed, "https://verify.local");
      const tokenParam = parsed.searchParams.get("token");
      if (tokenParam) {
        return tokenParam.trim();
      }
    }
  } catch {
    // Fall back to treating the raw string as an opaque token.
  }
  return trimmed;
}

const ACTION_LABELS: Record<string, string> = {
  CERTIFICATE_ISSUED: "Certificate Issued",
  CERTIFICATE_REVOKED: "Certificate Revoked",
  CERTIFICATE_DOWNLOADED: "Certificate Downloaded",
};

export function formatAuditActionLabel(action: string): string {
  if (!action) return "Activity";
  if (ACTION_LABELS[action]) {
    return ACTION_LABELS[action];
  }
  return action
    .split("_")
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(" ");
}

export function summarizeAuditDetails(details: Record<string, unknown> | null | undefined): string {
  if (!details) return "";
  const entries = Object.entries(details).filter(
    ([, value]) => value !== null && value !== undefined && String(value).trim().length > 0
  );
  if (entries.length === 0) return "";
  return entries
    .slice(0, 3)
    .map(([key, value]) => `${key}: ${String(value)}`)
    .join(" · ");
}

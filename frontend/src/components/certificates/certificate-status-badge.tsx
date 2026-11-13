import { Badge } from "@/components/ui/badge";
import type { CertificateStatus } from "@/lib/api/types";

const STATUS_META: Record<
  CertificateStatus,
  { label: string; variant: "success" | "danger" | "warning" | "neutral" }
> = {
  ISSUED: { label: "Issued", variant: "success" },
  REVOKED: { label: "Revoked", variant: "danger" },
  PENDING: { label: "Pending", variant: "warning" },
  EXPIRED: { label: "Expired", variant: "neutral" },
  FAILED: { label: "Failed", variant: "danger" },
};

export function CertificateStatusBadge({ status }: { status: CertificateStatus }) {
  const meta = STATUS_META[status] ?? { label: status, variant: "neutral" as const };
  return <Badge variant={meta.variant}>{meta.label}</Badge>;
}

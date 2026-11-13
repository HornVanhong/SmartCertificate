import { Badge } from "@/components/ui/badge";
import type { TemplateStatus } from "@/lib/api/types";

export function TemplateStatusBadge({ status }: { status: TemplateStatus }) {
  if (status === "PUBLISHED") {
    return <Badge variant="success">Published</Badge>;
  }
  if (status === "ARCHIVED") {
    return <Badge variant="neutral">Archived</Badge>;
  }
  return <Badge variant="warning">Draft</Badge>;
}

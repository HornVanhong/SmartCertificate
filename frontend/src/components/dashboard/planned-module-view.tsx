import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { StatePanel } from "@/components/ui/state-panel";

interface PlannedModuleViewProps {
  title: string;
  description: string;
  phaseLabel: string;
  plannedCapabilities: string[];
}

export function PlannedModuleView({
  title,
  description,
  phaseLabel,
  plannedCapabilities,
}: PlannedModuleViewProps) {
  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2.5">
            <h1 className="text-2xl font-semibold tracking-tight text-text-main">{title}</h1>
            <Badge variant="navy">{phaseLabel}</Badge>
          </div>
          <p className="mt-1 text-sm text-text-secondary">{description}</p>
        </div>
        <Link
          href="/"
          className="inline-flex items-center gap-1.5 rounded-lg border border-border-subtle bg-surface px-3 py-2 text-sm font-medium text-text-main hover:bg-slate-50"
        >
          <ArrowLeft className="h-4 w-4" />
          Back to Dashboard
        </Link>
      </div>

      <StatePanel
        variant="empty"
        title="Module scheduled in roadmap"
        description="This workspace route is scaffolded as part of the Phase 1 dashboard shell. Backend endpoints and interactive workflows will be connected in its dedicated phase."
      />

      <Card>
        <CardHeader>
          <CardTitle>Planned Scope</CardTitle>
          <CardDescription>Capabilities specified for this module</CardDescription>
        </CardHeader>
        <CardContent>
          <ul className="grid gap-2 text-sm text-text-secondary sm:grid-cols-2">
            {plannedCapabilities.map((item) => (
              <li
                key={item}
                className="flex items-start gap-2 rounded-lg border border-border-subtle bg-bg-app px-3 py-2 text-text-main"
              >
                <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-brand-gold" />
                <span>{item}</span>
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}

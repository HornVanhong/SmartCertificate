"use client";

import Link from "next/link";
import { useAuth } from "@/components/auth/auth-provider";
import { StatePanel } from "@/components/ui/state-panel";

export function RequireAuthPanel({ children }: { children: React.ReactNode }) {
  const { status } = useAuth();

  if (status === "loading") {
    return (
      <StatePanel
        variant="loading"
        title="Checking session"
        description="Verifying your organization workspace session..."
      />
    );
  }

  if (status === "unauthenticated") {
    return (
      <StatePanel
        variant="empty"
        title="Sign in required"
        description="Authenticate with your organization account or register a new organization workspace to access this section."
      >
        <div className="flex flex-wrap items-center justify-center gap-3">
          <Link
            href="/login"
            className="inline-flex h-9 items-center justify-center rounded-lg bg-brand-navy px-4 text-sm font-medium text-white hover:bg-[#1b3a63]"
          >
            Sign In
          </Link>
          <Link
            href="/register"
            className="inline-flex h-9 items-center justify-center rounded-lg border border-border-subtle bg-surface px-4 text-sm font-medium text-text-main hover:bg-slate-50"
          >
            Create Organization
          </Link>
        </div>
      </StatePanel>
    );
  }

  return <>{children}</>;
}

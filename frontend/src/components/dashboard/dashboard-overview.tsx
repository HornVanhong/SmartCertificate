"use client";

import * as React from "react";
import Link from "next/link";
import {
  Activity,
  Award,
  BarChart3,
  CheckCircle2,
  FileSpreadsheet,
  Info,
  LayoutTemplate,
  PieChart,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  X,
} from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { SystemStatusCard } from "@/components/dashboard/system-status-card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { StatePanel } from "@/components/ui/state-panel";
import {
  fetchDashboardSummary,
  fetchIssuanceTrends,
  fetchRecentActivity,
} from "@/lib/api/dashboard";
import {
  ApiClientError,
  type CertificateAuditLog,
  type DashboardSummary,
  type IssuanceTrendPoint,
} from "@/lib/api/types";
import { formatAuditActionLabel, summarizeAuditDetails } from "@/lib/verification/token-utils";

type DashboardDataState = {
  loading: boolean;
  error: string | null;
  summary: DashboardSummary | null;
  trends: IssuanceTrendPoint[];
  activity: CertificateAuditLog[];
};

const TREND_WINDOW_OPTIONS = [7, 14, 30] as const;

function formatTimestamp(isoString: string): string {
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return isoString;
  return date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

function formatShortDate(isoDate: string): string {
  const parsed = new Date(`${isoDate}T00:00:00Z`);
  if (Number.isNaN(parsed.getTime())) return isoDate;
  return parsed.toLocaleDateString(undefined, { month: "short", day: "numeric", timeZone: "UTC" });
}

function actionBadgeVariant(action: string): "success" | "danger" | "blue" | "neutral" {
  if (action === "CERTIFICATE_ISSUED") return "success";
  if (action === "CERTIFICATE_REVOKED") return "danger";
  if (action === "CERTIFICATE_DOWNLOADED") return "blue";
  return "neutral";
}

function CredentialDonutChart({ summary }: { summary: DashboardSummary | null }) {
  const issued = summary?.issuedCertificates ?? 0;
  const revoked = summary?.revokedCertificates ?? 0;
  const publishedTpl = summary?.publishedTemplates ?? 0;
  const draftTpl = summary?.draftTemplates ?? 0;

  const segments = [
    { label: "Active Issued", count: issued, color: "#10B981" },
    { label: "Revoked", count: revoked, color: "#F59E0B" },
    { label: "Published Templates", count: publishedTpl, color: "#2563EB" },
    { label: "Draft Templates", count: draftTpl, color: "#8B5CF6" },
  ];

  const total = segments.reduce((sum, seg) => sum + seg.count, 0);
  const radius = 44;
  const circumference = 2 * Math.PI * radius;

  const strokeSlices = segments.reduce<
    Array<{
      label: string;
      count: number;
      color: string;
      dashArray: string;
      dashOffset: number;
    }>
  >((acc, seg) => {
    const prevUsed = acc.reduce((sum, item) => sum + item.count, 0);
    const ratio = total > 0 ? seg.count / total : 0;
    const length = ratio * circumference;
    const startOffset = total > 0 ? -(prevUsed / total) * circumference : 0;
    acc.push({
      ...seg,
      dashArray: `${length} ${Math.max(0, circumference - length)}`,
      dashOffset: startOffset,
    });
    return acc;
  }, []);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-center py-2">
        <div className="relative h-36 w-36">
          <svg viewBox="0 0 120 120" className="h-full w-full -rotate-90">
            <circle cx="60" cy="60" r={radius} fill="none" stroke="#E2E8F0" strokeWidth="16" />
            {total > 0
              ? strokeSlices.map((seg) => {
                  if (seg.count === 0) return null;
                  return (
                    <circle
                      key={seg.label}
                      cx="60"
                      cy="60"
                      r={radius}
                      fill="none"
                      stroke={seg.color}
                      strokeWidth="16"
                      strokeDasharray={seg.dashArray}
                      strokeDashoffset={seg.dashOffset}
                      strokeLinecap="butt"
                    />
                  );
                })
              : null}
          </svg>
          <div className="absolute inset-0 flex flex-col items-center justify-center text-center">
            <span className="text-xl font-bold text-text-main">{total}</span>
            <span className="text-[10px] font-medium tracking-wide text-text-secondary uppercase">
              Records
            </span>
          </div>
        </div>
      </div>

      <ul className="space-y-2 text-xs">
        {segments.map((seg) => {
          const pct = total > 0 ? ((seg.count / total) * 100).toFixed(1) : "0.0";
          return (
            <li key={seg.label} className="flex items-center justify-between gap-2">
              <span className="flex items-center gap-2 text-text-main">
                <span
                  className="h-2.5 w-2.5 shrink-0 rounded-full"
                  style={{ backgroundColor: seg.color }}
                />
                {seg.label}
              </span>
              <span className="font-medium text-text-secondary">
                {seg.count} ({pct}%)
              </span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

export function DashboardOverview() {
  const { status, session, hasPermission } = useAuth();
  const [days, setDays] = React.useState<(typeof TREND_WINDOW_OPTIONS)[number]>(14);
  const [refreshTick, setRefreshTick] = React.useState(0);
  const [bannerDismissed, setBannerDismissed] = React.useState(false);
  const [state, setState] = React.useState<DashboardDataState>({
    loading: true,
    error: null,
    summary: null,
    trends: [],
    activity: [],
  });

  React.useEffect(() => {
    if (status !== "authenticated") {
      return;
    }

    const controller = new AbortController();
    let active = true;

    Promise.all([
      fetchDashboardSummary(controller.signal),
      fetchIssuanceTrends(days, controller.signal),
      fetchRecentActivity(8, controller.signal),
    ])
      .then(([summaryRes, trendsRes, activityRes]) => {
        if (!active) return;
        setState({
          loading: false,
          error: null,
          summary: summaryRes.data,
          trends: trendsRes.data,
          activity: activityRes.data,
        });
      })
      .catch((err: unknown) => {
        if (!active || controller.signal.aborted) return;
        const message =
          err instanceof ApiClientError
            ? err.message
            : "Unable to load organization dashboard metrics right now.";
        setState((prev) => ({
          ...prev,
          loading: false,
          error: message,
        }));
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [status, days, refreshTick]);

  const handleRefresh = () => {
    setState((prev) => ({ ...prev, loading: true, error: null }));
    setRefreshTick((tick) => tick + 1);
  };

  const handleDaysChange = (nextDays: (typeof TREND_WINDOW_OPTIONS)[number]) => {
    if (nextDays === days) return;
    setState((prev) => ({ ...prev, loading: true, error: null }));
    setDays(nextDays);
  };

  if (status === "loading") {
    return (
      <div className="space-y-6">
        <StatePanel
          variant="loading"
          title="Loading workspace"
          description="Restoring your organization session and security context..."
        />
        <SystemStatusCard />
      </div>
    );
  }

  if (status === "unauthenticated" || !session) {
    return (
      <div className="space-y-6">
        <Card className="overflow-hidden border-brand-navy/15 bg-gradient-to-br from-[#0B1324] via-brand-navy to-[#1e3a5f] text-white shadow-md">
          <CardHeader className="p-6 sm:p-8">
            <div className="flex flex-wrap items-center justify-between gap-6">
              <div className="max-w-2xl space-y-2.5">
                <Badge
                  variant="gold"
                  className="border-brand-gold/40 bg-brand-gold/20 text-brand-gold"
                >
                  Organization Credential Platform
                </Badge>
                <CardTitle className="text-2xl font-bold tracking-tight text-white">
                  Smart Certificate Generator
                </CardTitle>
                <CardDescription className="text-sm leading-relaxed text-slate-200">
                  Design bilingual English &amp; Khmer templates with Fabric.js, generate crisp PDFs
                  with cryptographic QR codes, run bulk CSV batches, and verify credentials
                  publicly.
                </CardDescription>
              </div>
              <div className="flex flex-wrap gap-2.5">
                <Link
                  href="/login"
                  className="inline-flex h-10 items-center justify-center rounded-lg bg-[#2563EB] px-5 text-sm font-medium text-white shadow-xs transition-colors hover:bg-[#1D4ED8]"
                >
                  Sign In
                </Link>
                <Link
                  href="/register"
                  className="inline-flex h-10 items-center justify-center rounded-lg border border-white/25 bg-white/10 px-5 text-sm font-medium text-white transition-colors hover:bg-white/20"
                >
                  Create Organization
                </Link>
                <Link
                  href="/verify"
                  className="inline-flex h-10 items-center justify-center rounded-lg border border-brand-gold/50 bg-brand-gold/20 px-4 text-sm font-medium text-brand-gold transition-colors hover:bg-brand-gold/30"
                >
                  Verify Certificate
                </Link>
              </div>
            </div>
          </CardHeader>
        </Card>
        <SystemStatusCard />
      </div>
    );
  }

  const summary = state.summary;
  const maxDailyCount = Math.max(1, ...state.trends.map((point) => point.issuedCount));
  const midTick = Math.max(1, Math.ceil(maxDailyCount / 2));
  const totalWindowIssued = state.trends.reduce((sum, point) => sum + point.issuedCount, 0);

  return (
    <div className="space-y-6">
      {/* Top workspace banner matching reference CertLister bar */}
      {!bannerDismissed ? (
        <div className="flex items-center justify-between gap-3 rounded-xl border border-sky-200 bg-sky-50/90 px-4 py-2.5 text-xs text-sky-900">
          <div className="flex items-center gap-2.5">
            <Info className="h-4 w-4 shrink-0 text-[#2563EB]" />
            <span>
              Connected to <strong>{session.organization.name}</strong> · Open{" "}
              <Link href="/studio/templates" className="font-semibold underline">
                Design Studio
              </Link>{" "}
              to customize layouts or{" "}
              <Link href="/studio/new" className="font-semibold underline">
                issue a verified certificate
              </Link>
              .
            </span>
          </div>
          <button
            type="button"
            aria-label="Dismiss workspace notice"
            onClick={() => setBannerDismissed(true)}
            className="rounded p-1 text-sky-700 hover:bg-sky-200/60"
          >
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      ) : null}

      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-text-main">Dashboard</h1>
            <Badge variant={summary?.scope === "ORGANIZATION" ? "navy" : "blue"}>
              {summary?.scope === "ORGANIZATION" ? "Organization Scope" : "Personal Scope"}
            </Badge>
          </div>
          <p className="text-xs text-text-secondary">
            Real-time credential metrics, template inventory, and batch processing across{" "}
            {session.organization.name}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {hasPermission("certificate:issue") ? (
            <Link
              href="/studio/new"
              className="inline-flex h-9 items-center gap-2 rounded-lg bg-[#2563EB] px-3.5 text-sm font-medium text-white shadow-xs transition-colors hover:bg-[#1D4ED8]"
            >
              <Sparkles className="h-4 w-4" />
              Issue Certificate
            </Link>
          ) : null}
          {hasPermission("template:read") ? (
            <Link
              href="/studio/templates"
              className="inline-flex h-9 items-center gap-2 rounded-lg border border-border-subtle bg-surface px-3 text-sm font-medium text-text-main transition-colors hover:bg-slate-50"
            >
              <LayoutTemplate className="h-4 w-4 text-brand-navy" />
              Templates
            </Link>
          ) : null}
          {hasPermission("certificate:bulk_generate") ? (
            <Link
              href="/certificates/bulk"
              className="inline-flex h-9 items-center gap-2 rounded-lg border border-border-subtle bg-surface px-3 text-sm font-medium text-text-main transition-colors hover:bg-slate-50"
            >
              <FileSpreadsheet className="h-4 w-4 text-emerald-700" />
              Bulk CSV
            </Link>
          ) : null}
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleRefresh}
            disabled={state.loading}
          >
            <RefreshCw className={`h-3.5 w-3.5 ${state.loading ? "animate-spin" : ""}`} />
            Refresh
          </Button>
        </div>
      </div>

      {state.error ? (
        <StatePanel variant="error" title="Dashboard metrics unavailable" description={state.error}>
          <Button type="button" variant="outline" size="sm" onClick={handleRefresh}>
            Retry
          </Button>
        </StatePanel>
      ) : null}

      {/* KPI Cards matching CertLister 5/4 card strip */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Card className="border-border-subtle/80 shadow-xs transition-shadow hover:shadow-sm">
          <CardContent className="p-5">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-[11px] font-semibold tracking-wider text-text-secondary uppercase">
                  Total Certificates
                </p>
                <p className="mt-2 text-3xl font-bold tracking-tight text-text-main">
                  {summary ? summary.totalCertificates : "—"}
                </p>
              </div>
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-blue-50 text-[#2563EB]">
                <Award className="h-5 w-5" />
              </div>
            </div>
            <p className="mt-3 text-xs font-medium text-emerald-700">
              {summary
                ? `^ ${summary.issuedLast30Days} issued in last 30 days`
                : "Loading totals..."}
            </p>
          </CardContent>
        </Card>

        <Card className="border-border-subtle/80 shadow-xs transition-shadow hover:shadow-sm">
          <CardContent className="p-5">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-[11px] font-semibold tracking-wider text-text-secondary uppercase">
                  Active Issued
                </p>
                <p className="mt-2 text-3xl font-bold tracking-tight text-text-main">
                  {summary ? summary.issuedCertificates : "—"}
                </p>
              </div>
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600">
                <CheckCircle2 className="h-5 w-5" />
              </div>
            </div>
            <p className="mt-3 text-xs text-text-secondary">
              Verified VALID credentials with QR proof
            </p>
          </CardContent>
        </Card>

        <Card className="border-border-subtle/80 shadow-xs transition-shadow hover:shadow-sm">
          <CardContent className="p-5">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-[11px] font-semibold tracking-wider text-text-secondary uppercase">
                  Revoked Certificates
                </p>
                <p className="mt-2 text-3xl font-bold tracking-tight text-text-main">
                  {summary ? summary.revokedCertificates : "—"}
                </p>
              </div>
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-amber-50 text-amber-600">
                <ShieldAlert className="h-5 w-5" />
              </div>
            </div>
            <p className="mt-3 text-xs text-text-secondary">
              Flagged REVOKED instantly on verification lookup
            </p>
          </CardContent>
        </Card>

        <Card className="border-border-subtle/80 shadow-xs transition-shadow hover:shadow-sm">
          <CardContent className="p-5">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-[11px] font-semibold tracking-wider text-text-secondary uppercase">
                  Templates &amp; Batches
                </p>
                <p className="mt-2 text-3xl font-bold tracking-tight text-text-main">
                  {summary ? summary.publishedTemplates : "—"}
                </p>
              </div>
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-violet-50 text-violet-600">
                <LayoutTemplate className="h-5 w-5" />
              </div>
            </div>
            <p className="mt-3 text-xs text-text-secondary">
              {summary
                ? `${summary.draftTemplates} drafts · ${summary.totalBatches} batches (${summary.activeBatches} active)`
                : "Loading templates..."}
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Analytics Row: Bar Chart + Donut Breakdown */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="border-border-subtle/80 shadow-xs lg:col-span-2">
          <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3">
            <div>
              <CardTitle className="flex items-center gap-2 text-base">
                <BarChart3 className="h-4 w-4 text-[#2563EB]" />
                Certificates Issued ({days} Days)
              </CardTitle>
              <CardDescription>
                {totalWindowIssued} credential{totalWindowIssued === 1 ? "" : "s"} issued across the
                selected {days}-day UTC window
              </CardDescription>
            </div>
            <div className="flex items-center gap-1 rounded-lg border border-border-subtle bg-slate-50 p-1">
              {TREND_WINDOW_OPTIONS.map((option) => (
                <button
                  key={option}
                  type="button"
                  onClick={() => handleDaysChange(option)}
                  className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
                    days === option
                      ? "bg-[#2563EB] text-white shadow-2xs"
                      : "text-text-secondary hover:text-text-main"
                  }`}
                >
                  {option}d
                </button>
              ))}
            </div>
          </CardHeader>
          <CardContent>
            {state.loading && state.trends.length === 0 ? (
              <div className="h-52 animate-pulse rounded-lg bg-slate-100" />
            ) : state.trends.length === 0 ? (
              <p className="py-14 text-center text-sm text-text-secondary">
                No issuance records available in this window.
              </p>
            ) : (
              <div className="space-y-3">
                <div className="flex gap-3">
                  <div className="flex h-52 flex-col justify-between py-1 text-[11px] font-medium text-text-secondary">
                    <span>{maxDailyCount}</span>
                    <span>{midTick}</span>
                    <span>0</span>
                  </div>
                  <div
                    className="flex h-52 flex-1 items-end gap-1.5 rounded-xl border border-border-subtle bg-slate-50/50 px-3 pt-4 pb-2"
                    role="img"
                    aria-label={`Daily certificate issuance chart over ${days} days`}
                  >
                    {state.trends.map((point) => {
                      const heightPct =
                        point.issuedCount === 0
                          ? 4
                          : Math.max(12, Math.round((point.issuedCount / maxDailyCount) * 100));
                      return (
                        <div
                          key={point.date}
                          title={`${point.date}: ${point.issuedCount} issued`}
                          className="group flex h-full flex-1 flex-col items-center justify-end gap-1"
                        >
                          <span className="text-[10px] font-semibold text-[#2563EB] opacity-0 transition-opacity group-hover:opacity-100">
                            {point.issuedCount}
                          </span>
                          <div
                            className={`w-full max-w-6 rounded-t-md transition-all ${
                              point.issuedCount > 0
                                ? "bg-[#2563EB] group-hover:bg-[#1D4ED8]"
                                : "bg-slate-200/80"
                            }`}
                            style={{ height: `${heightPct}%` }}
                          />
                        </div>
                      );
                    })}
                  </div>
                </div>
                <div className="flex items-center justify-between pl-6 text-xs text-text-secondary">
                  <span>{formatShortDate(state.trends[0].date)}</span>
                  <span>Peak day: {maxDailyCount} issued</span>
                  <span>{formatShortDate(state.trends[state.trends.length - 1].date)}</span>
                </div>
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="border-border-subtle/80 shadow-xs">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <PieChart className="h-4 w-4 text-[#2563EB]" />
              Credential &amp; Template Mix
            </CardTitle>
            <CardDescription>Distribution across credentials and template versions</CardDescription>
          </CardHeader>
          <CardContent>
            <CredentialDonutChart summary={summary} />
          </CardContent>
        </Card>
      </div>

      {/* Bottom Row: Recent Activity + Platform Status */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <Card className="border-border-subtle/80 shadow-xs lg:col-span-2">
          <CardHeader className="flex flex-row items-center justify-between gap-2">
            <div>
              <CardTitle className="flex items-center gap-2 text-base">
                <Activity className="h-4 w-4 text-[#2563EB]" />
                Recent Activity
              </CardTitle>
              <CardDescription>
                {hasPermission("audit:read")
                  ? "Organization-wide credential & template audit stream"
                  : "Your recent issuance & credential events"}
              </CardDescription>
            </div>
            {hasPermission("audit:read") ? (
              <Link
                href="/activity-logs"
                className="text-xs font-medium text-[#2563EB] hover:underline"
              >
                View all logs →
              </Link>
            ) : null}
          </CardHeader>
          <CardContent>
            {state.loading && state.activity.length === 0 ? (
              <div className="space-y-2.5">
                {[0, 1, 2, 3].map((idx) => (
                  <div key={idx} className="h-12 animate-pulse rounded-lg bg-slate-100" />
                ))}
              </div>
            ) : state.activity.length === 0 ? (
              <p className="py-8 text-center text-sm text-text-secondary">
                No audit events recorded yet.
              </p>
            ) : (
              <ul className="divide-y divide-border-subtle">
                {state.activity.map((event) => {
                  const detailSummary = summarizeAuditDetails(event.details);
                  return (
                    <li
                      key={event.id}
                      className="flex flex-wrap items-center justify-between gap-2 py-2.5 first:pt-0 last:pb-0"
                    >
                      <div className="min-w-0 flex-1 space-y-0.5">
                        <div className="flex items-center gap-2">
                          <Badge variant={actionBadgeVariant(event.action)}>
                            {formatAuditActionLabel(event.action)}
                          </Badge>
                          {event.reason ? (
                            <span className="truncate text-xs font-medium text-text-main">
                              {event.reason}
                            </span>
                          ) : null}
                        </div>
                        {detailSummary ? (
                          <p className="truncate text-[11px] text-text-secondary">
                            {detailSummary}
                          </p>
                        ) : null}
                      </div>
                      <span className="shrink-0 text-[11px] text-text-secondary">
                        {formatTimestamp(event.createdAt)}
                      </span>
                    </li>
                  );
                })}
              </ul>
            )}
          </CardContent>
        </Card>

        <div className="space-y-4">
          <SystemStatusCard />
          <Card className="border-border-subtle/80 shadow-xs">
            <CardContent className="flex items-center justify-between gap-3 p-4">
              <div className="flex items-center gap-2.5">
                <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand-gold/15 text-[#7c6122]">
                  <ShieldCheck className="h-4 w-4" />
                </div>
                <div>
                  <p className="text-xs font-semibold text-text-main">Public Registry</p>
                  <p className="text-[11px] text-text-secondary">Verify any QR token live</p>
                </div>
              </div>
              <Link
                href="/verify"
                className="rounded-lg border border-border-subtle px-2.5 py-1.5 text-xs font-medium text-text-main hover:bg-slate-50"
              >
                Open
              </Link>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}

"use client";

import * as React from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Award,
  CheckCircle2,
  ChevronRight,
  ExternalLink,
  LogIn,
  LogOut,
  Menu,
  ShieldCheck,
  Sparkles,
  X,
} from "lucide-react";
import { useAuth } from "@/components/auth/auth-provider";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { NAVIGATION_ITEMS } from "@/config/navigation";
import { cn } from "@/lib/utils";

interface DashboardShellProps {
  children: React.ReactNode;
}

const ROUTE_TITLES: Array<{ match: (path: string) => boolean; title: string; subtitle: string }> = [
  {
    match: (p) => p === "/",
    title: "Overview",
    subtitle: "Organization KPIs, issuance trends, and audit telemetry",
  },
  {
    match: (p) => p.startsWith("/studio/templates/"),
    title: "Design Studio",
    subtitle: "Interactive Fabric.js certificate layout & placeholder editor",
  },
  {
    match: (p) => p === "/studio/templates",
    title: "Template Gallery",
    subtitle: "Curated and organization certificate designs",
  },
  {
    match: (p) => p === "/studio/designs",
    title: "My Designs",
    subtitle: "Draft layouts and unpublished iterations",
  },
  {
    match: (p) => p === "/studio/new",
    title: "Issue Certificate",
    subtitle: "Generate verified PDF/PNG credentials with QR proof",
  },
  {
    match: (p) => p.startsWith("/certificates/bulk"),
    title: "Bulk CSV Issuance",
    subtitle: "Batch import, column mapping, validation, and ZIP export",
  },
  {
    match: (p) => p.startsWith("/certificates/revoked"),
    title: "Revoked Certificates",
    subtitle: "Revoked credential audit and status ledger",
  },
  {
    match: (p) => p.startsWith("/certificates"),
    title: "Certificates",
    subtitle: "Issued credentials, previews, downloads, and revocation",
  },
  {
    match: (p) => p.startsWith("/organization"),
    title: "Organization & Branding",
    subtitle: "Workspace profile, logos, signatures, and official stamps",
  },
  {
    match: (p) => p.startsWith("/users"),
    title: "Users & Roles",
    subtitle: "Organization members, invitations, and RBAC assignments",
  },
  {
    match: (p) => p.startsWith("/activity-logs"),
    title: "Activity Logs",
    subtitle: "Filterable organization security and issuance audit trail",
  },
  {
    match: (p) => p.startsWith("/settings"),
    title: "Settings",
    subtitle: "Workspace security and platform defaults",
  },
];

function getInitials(name: string | undefined | null): string {
  if (!name) return "SC";
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "SC";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[parts.length - 1][0]}`.toUpperCase();
}

export function DashboardShell({ children }: DashboardShellProps) {
  const pathname = usePathname();
  const router = useRouter();
  const { status, session, logout } = useAuth();
  const [mobileOpenForPath, setMobileOpenForPath] = React.useState<string | null>(null);
  const mobileMenuOpen = mobileOpenForPath === pathname;

  const closeMobileMenu = () => setMobileOpenForPath(null);
  const openMobileMenu = () => setMobileOpenForPath(pathname);

  const handleLogout = async () => {
    await logout();
    router.push("/login");
  };

  const isRouteActive = (href: string) => {
    if (href === "/") {
      return pathname === "/";
    }
    return pathname === href || pathname.startsWith(`${href}/`);
  };

  const currentMeta = ROUTE_TITLES.find((item) => item.match(pathname)) ?? {
    title: "Workspace",
    subtitle: "Smart Certificate Generator",
  };

  const renderNavContent = () => (
    <div className="flex h-full flex-col bg-[#0B1324]">
      <div className="flex h-16 items-center gap-3 border-b border-white/10 px-5">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-[#2563EB] to-[#1D4ED8] text-white shadow-sm ring-1 ring-white/15">
          <Award className="h-5 w-5" />
        </div>
        <div className="min-w-0">
          <p className="truncate text-sm font-bold tracking-wider text-white uppercase">
            SmartCert
          </p>
          <p className="truncate text-[11px] text-slate-400">Design. Issue. Verify.</p>
        </div>
      </div>

      <nav aria-label="Primary navigation" className="flex-1 space-y-1.5 overflow-y-auto px-3 py-4">
        {NAVIGATION_ITEMS.map((item) => {
          const Icon = item.icon;
          const active = isRouteActive(item.href);

          return (
            <div key={item.href} className="space-y-1">
              <Link
                href={item.href}
                onClick={closeMobileMenu}
                className={cn(
                  "flex items-center justify-between rounded-lg px-3 py-2 text-sm font-medium transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-gold",
                  active
                    ? "border border-[#2563EB]/40 bg-[#2563EB]/20 text-white shadow-xs"
                    : "border border-transparent text-slate-300 hover:bg-white/6 hover:text-white"
                )}
              >
                <span className="flex items-center gap-2.5">
                  <Icon className={cn("h-4 w-4", active ? "text-[#60A5FA]" : "text-slate-400")} />
                  {item.label}
                </span>
                {item.children ? (
                  <ChevronRight
                    className={cn(
                      "h-3.5 w-3.5 text-slate-500 transition-transform",
                      active && "rotate-90 text-[#60A5FA]"
                    )}
                  />
                ) : null}
              </Link>

              {item.children ? (
                <div className="ml-4 space-y-0.5 border-l border-white/10 pl-3">
                  {item.children.map((child) => {
                    const ChildIcon = child.icon;
                    const childActive = pathname === child.href;
                    return (
                      <Link
                        key={child.href}
                        href={child.href}
                        onClick={closeMobileMenu}
                        className={cn(
                          "flex items-center gap-2 rounded-md px-2.5 py-1.5 text-xs font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-gold",
                          childActive
                            ? "bg-white/10 text-white"
                            : "text-slate-400 hover:bg-white/5 hover:text-slate-200"
                        )}
                      >
                        <ChildIcon
                          className={cn(
                            "h-3.5 w-3.5 shrink-0",
                            childActive ? "text-brand-gold" : "text-slate-500"
                          )}
                        />
                        <span className="truncate">{child.label}</span>
                      </Link>
                    );
                  })}
                </div>
              ) : null}
            </div>
          );
        })}
      </nav>

      <div className="space-y-3 border-t border-white/10 p-3.5">
        {session ? (
          <>
            <Link
              href="/studio/templates"
              onClick={closeMobileMenu}
              className="block rounded-xl border border-white/10 bg-white/5 p-3 transition-colors hover:bg-white/10"
            >
              <div className="flex items-center justify-between text-[10px] font-semibold tracking-wider text-slate-400 uppercase">
                <span className="flex items-center gap-1">
                  <Sparkles className="h-3 w-3 text-brand-gold" />
                  Quick Studio
                </span>
                <span className="text-emerald-400">Ready</span>
              </div>
              <p className="mt-1 flex items-center gap-1.5 text-xs font-medium text-white">
                <CheckCircle2 className="h-3.5 w-3.5 text-[#60A5FA]" />
                Open Template Gallery
              </p>
              <p className="mt-0.5 text-[11px] text-slate-400">
                Design, preview, or publish layouts
              </p>
            </Link>

            <div className="flex items-center gap-2.5 rounded-xl bg-white/6 p-2.5 text-xs text-slate-200">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-brand-gold/20 font-semibold text-brand-gold ring-1 ring-brand-gold/40">
                {getInitials(session.fullName)}
              </div>
              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between gap-1">
                  <span className="truncate font-semibold text-white">{session.fullName}</span>
                  <Badge
                    variant="gold"
                    className="shrink-0 border-brand-gold/40 bg-brand-gold/15 px-1.5 py-0 text-[10px] text-brand-gold"
                  >
                    {session.roleName}
                  </Badge>
                </div>
                <p className="truncate text-[11px] text-slate-400">{session.email}</p>
              </div>
            </div>
          </>
        ) : (
          <div className="rounded-xl border border-white/10 bg-white/6 p-3 text-xs text-slate-200">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-white">Guest Mode</span>
              <Badge
                variant="gold"
                className="border-brand-gold/40 bg-brand-gold/15 text-[10px] text-brand-gold"
              >
                Preview
              </Badge>
            </div>
            <p className="mt-1 text-[11px] leading-relaxed text-slate-400">
              Sign in or register an organization to design and issue certificates.
            </p>
          </div>
        )}
      </div>
    </div>
  );

  return (
    <div className="flex min-h-screen bg-bg-app text-text-main">
      {/* Desktop Sidebar */}
      <aside className="hidden w-64 shrink-0 border-r border-slate-900/10 bg-[#0B1324] lg:block">
        {renderNavContent()}
      </aside>

      {/* Mobile Drawer */}
      {mobileMenuOpen ? (
        <div className="fixed inset-0 z-50 flex lg:hidden" role="dialog" aria-modal="true">
          <div
            className="fixed inset-0 bg-slate-950/60 backdrop-blur-xs"
            onClick={closeMobileMenu}
          />
          <aside className="relative z-10 w-72 max-w-[85vw] bg-[#0B1324] shadow-2xl">
            <button
              type="button"
              aria-label="Close navigation menu"
              onClick={closeMobileMenu}
              className="absolute top-4 right-3 rounded-md p-1.5 text-slate-300 hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-gold"
            >
              <X className="h-5 w-5" />
            </button>
            {renderNavContent()}
          </aside>
        </div>
      ) : null}

      {/* Main Column */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between gap-4 border-b border-border-subtle bg-surface/95 px-4 backdrop-blur-xs sm:px-6">
          <div className="flex min-w-0 items-center gap-3">
            <Button
              variant="ghost"
              size="icon"
              className="lg:hidden"
              aria-label="Open navigation menu"
              onClick={openMobileMenu}
            >
              <Menu className="h-5 w-5" />
            </Button>
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="truncate text-sm font-semibold text-text-main">
                  {currentMeta.title}
                </span>
                <Badge variant="navy" className="hidden sm:inline-flex">
                  {session ? session.organization.name : "Organization Workspace"}
                </Badge>
              </div>
              <p className="hidden truncate text-[11px] text-text-secondary md:block">
                {currentMeta.subtitle}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Link
              href="/verify"
              className="hidden items-center gap-1.5 rounded-lg border border-border-subtle bg-slate-50/70 px-3 py-1.5 text-xs font-medium text-text-secondary transition-colors hover:bg-slate-100 hover:text-text-main sm:inline-flex"
            >
              <ShieldCheck className="h-3.5 w-3.5 text-[#2563EB]" />
              Verify Page
            </Link>
            <a
              href="/swagger-ui.html"
              target="_blank"
              rel="noreferrer"
              className="hidden items-center gap-1.5 rounded-lg border border-border-subtle px-3 py-1.5 text-xs font-medium text-text-secondary transition-colors hover:bg-slate-50 hover:text-text-main md:inline-flex"
            >
              API Docs
              <ExternalLink className="h-3.5 w-3.5" />
            </a>
            {status === "authenticated" ? (
              <Button variant="outline" size="sm" onClick={() => void handleLogout()}>
                <LogOut className="h-3.5 w-3.5" />
                Sign Out
              </Button>
            ) : status === "unauthenticated" ? (
              <Link
                href="/login"
                className="inline-flex items-center gap-1.5 rounded-lg bg-[#2563EB] px-3.5 py-1.5 text-xs font-medium text-white shadow-xs transition-colors hover:bg-[#1D4ED8]"
              >
                <LogIn className="h-3.5 w-3.5" />
                Sign In
              </Link>
            ) : null}
          </div>
        </header>

        <main className="flex-1 p-4 sm:p-6 lg:p-8">{children}</main>
      </div>
    </div>
  );
}

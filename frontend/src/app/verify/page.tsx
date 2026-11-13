"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import {
  ArrowLeft,
  Award,
  Building2,
  Calendar,
  Clock,
  Search,
  ShieldAlert,
  ShieldCheck,
  UserCheck,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { StatePanel } from "@/components/ui/state-panel";
import { verifyCertificateToken } from "@/lib/api/public-verify";
import { ApiClientError, type PublicVerificationResult } from "@/lib/api/types";
import { extractVerificationToken } from "@/lib/verification/token-utils";

function formatDate(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString(undefined, { dateStyle: "long", timeStyle: "short" });
}

function PublicVerifyContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const urlToken = searchParams.get("token") ?? "";

  const [tokenInput, setTokenInput] = React.useState(urlToken);
  const [lookupTick, setLookupTick] = React.useState(0);
  const [result, setResult] = React.useState<PublicVerificationResult | null>(null);
  const [loading, setLoading] = React.useState(Boolean(urlToken.trim()));
  const [errorState, setErrorState] = React.useState<{
    code: string;
    message: string;
  } | null>(null);

  React.useEffect(() => {
    const cleanToken = extractVerificationToken(urlToken);
    if (!cleanToken) {
      return;
    }

    const controller = new AbortController();
    let active = true;

    async function runVerification() {
      setLoading(true);
      setErrorState(null);
      setResult(null);
      try {
        const response = await verifyCertificateToken(cleanToken, controller.signal);
        if (!active) return;
        setResult(response.data);
      } catch (err) {
        if (!active || controller.signal.aborted) return;
        if (err instanceof ApiClientError) {
          setErrorState({ code: err.code, message: err.message });
        } else {
          setErrorState({
            code: "NETWORK_ERROR",
            message: "Unable to contact the verification service. Please try again.",
          });
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    void runVerification();
    return () => {
      active = false;
      controller.abort();
    };
  }, [urlToken, lookupTick]);

  const handleSubmit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const extracted = extractVerificationToken(tokenInput);
    if (!extracted) {
      setErrorState({
        code: "EMPTY_TOKEN",
        message: "Enter or paste a certificate verification token or QR verification URL.",
      });
      setResult(null);
      return;
    }
    setTokenInput(extracted);
    if (extracted === urlToken.trim()) {
      setLookupTick((value) => value + 1);
    } else {
      router.replace(`/verify?token=${encodeURIComponent(extracted)}`);
    }
  };

  return (
    <div className="min-h-screen bg-bg-app px-4 py-10 sm:py-14">
      <div className="mx-auto max-w-2xl space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-navy text-brand-gold shadow-xs">
              <ShieldCheck className="h-5 w-5" />
            </div>
            <div>
              <h1 className="text-xl font-semibold tracking-tight text-text-main">
                Public Certificate Verification
              </h1>
              <p className="text-xs text-text-secondary">
                Independent authenticity verification via Smart Certificate Generator
              </p>
            </div>
          </div>
          <Badge variant="navy">Verified Registry</Badge>
        </div>

        <Card>
          <CardHeader className="pb-4">
            <CardTitle className="text-base">Verify Certificate Token</CardTitle>
            <CardDescription>
              Scan the QR code on an issued certificate or paste its verification token / link
              below.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="flex flex-col gap-2 sm:flex-row">
              <div className="relative flex-1">
                <Search className="pointer-events-none absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 text-text-secondary" />
                <Input
                  value={tokenInput}
                  onChange={(event) => setTokenInput(event.target.value)}
                  placeholder="Paste verification token or https://.../verify?token=..."
                  className="pl-9 font-mono text-xs"
                  aria-label="Certificate verification token"
                />
              </div>
              <Button type="submit" variant="primary" disabled={loading}>
                {loading ? "Verifying..." : "Verify"}
              </Button>
            </form>
          </CardContent>
        </Card>

        {!urlToken.trim() && !loading && !result && !errorState ? (
          <StatePanel
            variant="empty"
            title="Ready to verify a credential"
            description="Open a certificate's QR verification URL or submit a verification token above to confirm its current validity."
          />
        ) : loading ? (
          <StatePanel
            variant="loading"
            title="Verifying certificate"
            description="Checking cryptographic token digest and revocation registry..."
          />
        ) : errorState ? (
          <StatePanel
            variant="error"
            title={
              errorState.code === "RATE_LIMITED"
                ? "Rate Limit Reached"
                : errorState.code === "NOT_FOUND"
                  ? "Certificate Not Found"
                  : "Verification Failed"
            }
            description={
              errorState.code === "NOT_FOUND"
                ? "No issued certificate matches this verification token. Check that the full token was copied without truncation."
                : errorState.message
            }
            actionLabel={urlToken.trim() ? "Try Again" : undefined}
            onAction={urlToken.trim() ? () => setLookupTick((value) => value + 1) : undefined}
          />
        ) : result ? (
          <Card
            className={
              result.status === "VALID"
                ? "border-emerald-200 bg-emerald-50/40"
                : result.status === "REVOKED"
                  ? "border-rose-200 bg-rose-50/40"
                  : "border-amber-200 bg-amber-50/40"
            }
          >
            <CardHeader>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div
                    className={
                      result.status === "VALID"
                        ? "flex h-10 w-10 items-center justify-center rounded-full bg-emerald-100 text-emerald-700"
                        : result.status === "REVOKED"
                          ? "flex h-10 w-10 items-center justify-center rounded-full bg-rose-100 text-rose-700"
                          : "flex h-10 w-10 items-center justify-center rounded-full bg-amber-100 text-amber-800"
                    }
                  >
                    {result.status === "VALID" ? (
                      <ShieldCheck className="h-5 w-5" />
                    ) : result.status === "REVOKED" ? (
                      <ShieldAlert className="h-5 w-5" />
                    ) : (
                      <Clock className="h-5 w-5" />
                    )}
                  </div>
                  <div>
                    <CardTitle className="text-lg">
                      {result.status === "VALID"
                        ? "Valid Certificate"
                        : result.status === "REVOKED"
                          ? "Certificate Revoked"
                          : "Certificate Expired"}
                    </CardTitle>
                    <CardDescription>
                      {result.status === "VALID"
                        ? "This credential is authentic, active, and issued by a verified organization."
                        : result.status === "REVOKED"
                          ? "This credential was issued previously but has been revoked by the issuing organization."
                          : "This credential has passed its expiration date."}
                    </CardDescription>
                  </div>
                </div>
                <Badge
                  variant={
                    result.status === "VALID"
                      ? "success"
                      : result.status === "REVOKED"
                        ? "danger"
                        : "warning"
                  }
                >
                  {result.status}
                </Badge>
              </div>
            </CardHeader>

            <CardContent>
              <div className="grid gap-4 rounded-xl border border-border-subtle bg-white p-5 sm:grid-cols-2">
                <div className="flex items-start gap-2.5">
                  <Award className="mt-0.5 h-4 w-4 shrink-0 text-brand-navy" />
                  <div>
                    <div className="text-xs text-text-secondary">Certificate Number</div>
                    <div className="font-mono text-sm font-semibold text-text-main">
                      {result.certificateNumber}
                    </div>
                  </div>
                </div>

                <div className="flex items-start gap-2.5">
                  <UserCheck className="mt-0.5 h-4 w-4 shrink-0 text-brand-navy" />
                  <div>
                    <div className="text-xs text-text-secondary">Recipient</div>
                    <div className="text-sm font-semibold text-text-main">
                      {result.recipientName}
                    </div>
                  </div>
                </div>

                <div className="flex items-start gap-2.5">
                  <Building2 className="mt-0.5 h-4 w-4 shrink-0 text-brand-navy" />
                  <div>
                    <div className="text-xs text-text-secondary">Issuing Organization</div>
                    <div className="text-sm font-medium text-text-main">
                      {result.organizationName}
                    </div>
                  </div>
                </div>

                <div className="flex items-start gap-2.5">
                  <Calendar className="mt-0.5 h-4 w-4 shrink-0 text-brand-navy" />
                  <div>
                    <div className="text-xs text-text-secondary">Course / Achievement</div>
                    <div className="text-sm font-medium text-text-main">{result.courseName}</div>
                  </div>
                </div>

                <div className="border-t border-border-subtle pt-3 sm:col-span-2">
                  <div className="grid gap-3 text-xs sm:grid-cols-3">
                    <div>
                      <span className="text-text-secondary">Issued On</span>
                      <div className="mt-0.5 font-medium text-text-main">
                        {formatDate(result.issuedAt)}
                      </div>
                    </div>
                    {result.expiresAt ? (
                      <div>
                        <span className="text-text-secondary">Expiration</span>
                        <div className="mt-0.5 font-medium text-text-main">
                          {formatDate(result.expiresAt)}
                        </div>
                      </div>
                    ) : null}
                    {result.revokedAt ? (
                      <div>
                        <span className="text-rose-700">Revoked On</span>
                        <div className="mt-0.5 font-medium text-rose-800">
                          {formatDate(result.revokedAt)}
                        </div>
                      </div>
                    ) : null}
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        ) : null}

        <div className="flex justify-between text-xs text-text-secondary">
          <span>Only approved public credential details are displayed.</span>
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 font-medium text-brand-blue hover:underline"
          >
            <ArrowLeft className="h-3.5 w-3.5" />
            Return to Workspace
          </Link>
        </div>
      </div>
    </div>
  );
}

export default function PublicVerifyPage() {
  return (
    <React.Suspense
      fallback={
        <div className="min-h-screen bg-bg-app px-4 py-12">
          <div className="mx-auto max-w-2xl">
            <StatePanel
              variant="loading"
              title="Loading verification portal"
              description="Preparing verification check..."
            />
          </div>
        </div>
      }
    >
      <PublicVerifyContent />
    </React.Suspense>
  );
}

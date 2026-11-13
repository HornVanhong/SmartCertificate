"use client";

import * as React from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, Save } from "lucide-react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useAuth } from "@/components/auth/auth-provider";
import { RequireAuthPanel } from "@/components/auth/require-auth-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { StatePanel } from "@/components/ui/state-panel";
import { resolveBackendAssetUrl } from "@/lib/api/client";
import { fetchCurrentOrganization, updateCurrentOrganization } from "@/lib/api/organizations";
import { ApiClientError, type OrganizationProfile } from "@/lib/api/types";

const orgSchema = z.object({
  name: z.string().min(1, "Organization name is required.").max(200),
  contactEmail: z.string().email("Invalid email.").or(z.literal("")),
  phone: z.string().max(50),
  address: z.string().max(500),
  website: z
    .string()
    .regex(/^$|^https?:\/\/.*/, "Website must start with http:// or https://")
    .max(300),
  primaryColor: z.string().regex(/^#[0-9a-fA-F]{6}$/, "Must be a 6-digit hex color."),
  accentColor: z.string().regex(/^#[0-9a-fA-F]{6}$/, "Must be a 6-digit hex color."),
  defaultIssuerTitle: z.string().max(120),
});

type OrgFormValues = z.infer<typeof orgSchema>;

function OrganizationProfileContent() {
  const { hasPermission, refreshSession } = useAuth();
  const canUpdate = hasPermission("org:update");

  const [org, setOrg] = React.useState<OrganizationProfile | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [feedback, setFeedback] = React.useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);
  const [reloadTick, setReloadTick] = React.useState(0);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<OrgFormValues>({
    resolver: zodResolver(orgSchema),
    defaultValues: {
      name: "",
      contactEmail: "",
      phone: "",
      address: "",
      website: "",
      primaryColor: "#142D4E",
      accentColor: "#C6A45B",
      defaultIssuerTitle: "",
    },
  });

  React.useEffect(() => {
    const controller = new AbortController();
    let active = true;

    fetchCurrentOrganization(controller.signal)
      .then((res) => {
        if (!active) return;
        const data = res.data;
        setOrg(data);
        reset({
          name: data.name,
          contactEmail: data.contactEmail ?? "",
          phone: data.phone ?? "",
          address: data.address ?? "",
          website: data.website ?? "",
          primaryColor: data.brandSettings?.primaryColor ?? "#142D4E",
          accentColor: data.brandSettings?.accentColor ?? "#C6A45B",
          defaultIssuerTitle: data.brandSettings?.defaultIssuerTitle ?? "",
        });
        setError(null);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (!active || (err instanceof DOMException && err.name === "AbortError")) {
          return;
        }
        setError(err instanceof ApiClientError ? err.message : "Failed to load organization.");
        setLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [reloadTick, reset]);

  const onSubmit = async (values: OrgFormValues) => {
    setFeedback(null);
    try {
      const response = await updateCurrentOrganization({
        name: values.name,
        logoAssetId: org?.logoAssetId ?? null,
        contactEmail: values.contactEmail,
        phone: values.phone,
        address: values.address,
        website: values.website,
        brandSettings: {
          primaryColor: values.primaryColor,
          accentColor: values.accentColor,
          defaultIssuerTitle: values.defaultIssuerTitle,
        },
      });
      setOrg(response.data);
      setFeedback({ type: "success", text: "Organization profile saved." });
      await refreshSession();
    } catch (err) {
      setFeedback({
        type: "error",
        text: err instanceof ApiClientError ? err.message : "Failed to save organization profile.",
      });
    }
  };

  if (loading) {
    return (
      <StatePanel
        variant="loading"
        title="Loading organization profile"
        description="Fetching organization details and brand settings..."
      />
    );
  }

  if (error || !org) {
    return (
      <StatePanel
        variant="error"
        title="Unable to load organization"
        description={error ?? "Organization details were unavailable."}
        actionLabel="Retry"
        onAction={() => {
          setLoading(true);
          setReloadTick((t) => t + 1);
        }}
      />
    );
  }

  const logoPreviewUrl = resolveBackendAssetUrl(org.logoUrl);

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <div className="flex items-center gap-2.5">
            <h1 className="text-2xl font-semibold tracking-tight text-text-main">
              Organization Profile
            </h1>
            <Badge variant="navy">{org.slug}</Badge>
          </div>
          <p className="mt-1 text-sm text-text-secondary">
            Manage your organization identity, contact details, and default certificate colors.
          </p>
        </div>
        {logoPreviewUrl ? (
          <div className="flex h-14 w-36 items-center justify-center rounded-lg border border-border-subtle bg-surface p-2">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={logoPreviewUrl}
              alt={`${org.name} logo`}
              className="max-h-full max-w-full object-contain"
            />
          </div>
        ) : null}
      </div>

      {feedback ? (
        <div
          role={feedback.type === "error" ? "alert" : "status"}
          className={`rounded-lg border px-4 py-2.5 text-sm ${
            feedback.type === "error"
              ? "border-rose-200 bg-rose-50 text-rose-700"
              : "border-emerald-200 bg-emerald-50 text-emerald-800"
          }`}
        >
          {feedback.text}
        </div>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>Profile &amp; Branding Details</CardTitle>
          <CardDescription>
            {canUpdate
              ? "Updates are scoped to your organization and recorded in the audit log."
              : "Read-only view. Administrator permissions are required to update settings."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5 sm:col-span-2">
                <label htmlFor="name" className="text-sm font-medium text-text-main">
                  Organization Name
                </label>
                <Input id="name" disabled={!canUpdate} {...register("name")} />
                {errors.name ? (
                  <p className="text-xs text-rose-600">{errors.name.message}</p>
                ) : null}
              </div>

              <div className="space-y-1.5">
                <label htmlFor="contactEmail" className="text-sm font-medium text-text-main">
                  Contact Email
                </label>
                <Input
                  id="contactEmail"
                  type="email"
                  disabled={!canUpdate}
                  {...register("contactEmail")}
                />
                {errors.contactEmail ? (
                  <p className="text-xs text-rose-600">{errors.contactEmail.message}</p>
                ) : null}
              </div>

              <div className="space-y-1.5">
                <label htmlFor="phone" className="text-sm font-medium text-text-main">
                  Phone
                </label>
                <Input id="phone" disabled={!canUpdate} {...register("phone")} />
              </div>

              <div className="space-y-1.5 sm:col-span-2">
                <label htmlFor="website" className="text-sm font-medium text-text-main">
                  Website
                </label>
                <Input
                  id="website"
                  placeholder="https://example.org"
                  disabled={!canUpdate}
                  {...register("website")}
                />
                {errors.website ? (
                  <p className="text-xs text-rose-600">{errors.website.message}</p>
                ) : null}
              </div>

              <div className="space-y-1.5 sm:col-span-2">
                <label htmlFor="address" className="text-sm font-medium text-text-main">
                  Address
                </label>
                <Input id="address" disabled={!canUpdate} {...register("address")} />
              </div>

              <div className="space-y-1.5">
                <label htmlFor="primaryColor" className="text-sm font-medium text-text-main">
                  Primary Brand Color
                </label>
                <Input id="primaryColor" disabled={!canUpdate} {...register("primaryColor")} />
                {errors.primaryColor ? (
                  <p className="text-xs text-rose-600">{errors.primaryColor.message}</p>
                ) : null}
              </div>

              <div className="space-y-1.5">
                <label htmlFor="accentColor" className="text-sm font-medium text-text-main">
                  Accent Gold/Secondary Color
                </label>
                <Input id="accentColor" disabled={!canUpdate} {...register("accentColor")} />
                {errors.accentColor ? (
                  <p className="text-xs text-rose-600">{errors.accentColor.message}</p>
                ) : null}
              </div>

              <div className="space-y-1.5 sm:col-span-2">
                <label htmlFor="defaultIssuerTitle" className="text-sm font-medium text-text-main">
                  Default Issuer Signatory Title
                </label>
                <Input
                  id="defaultIssuerTitle"
                  placeholder="Academic Director"
                  disabled={!canUpdate}
                  {...register("defaultIssuerTitle")}
                />
              </div>
            </div>

            {canUpdate ? (
              <div className="flex justify-end">
                <Button type="submit" disabled={isSubmitting}>
                  {isSubmitting ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <Save className="h-4 w-4" />
                  )}
                  {isSubmitting ? "Saving..." : "Save Profile"}
                </Button>
              </div>
            ) : null}
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

export default function OrganizationPage() {
  return (
    <RequireAuthPanel>
      <OrganizationProfileContent />
    </RequireAuthPanel>
  );
}

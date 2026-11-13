"use client";

import * as React from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { zodResolver } from "@hookform/resolvers/zod";
import { Award, Loader2 } from "lucide-react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useAuth } from "@/components/auth/auth-provider";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { registerAccount } from "@/lib/api/auth";
import { ApiClientError } from "@/lib/api/types";

const registerSchema = z.object({
  fullName: z.string().min(1, "Full name is required.").max(160),
  email: z.string().email("Enter a valid email address.").max(254),
  organizationName: z.string().min(1, "Organization name is required.").max(200),
  password: z
    .string()
    .min(8, "Password must be at least 8 characters.")
    .max(128, "Password must not exceed 128 characters."),
});

type RegisterFormValues = z.infer<typeof registerSchema>;

export default function RegisterPage() {
  const router = useRouter();
  const { setAuthenticatedSession } = useAuth();
  const [formError, setFormError] = React.useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: "", email: "", organizationName: "", password: "" },
  });

  const onSubmit = async (values: RegisterFormValues) => {
    setFormError(null);
    try {
      const response = await registerAccount(values);
      setAuthenticatedSession(response.data.session);
      router.push("/organization");
    } catch (err) {
      if (err instanceof ApiClientError) {
        Object.entries(err.fieldErrors).forEach(([field, message]) => {
          if (
            field === "fullName" ||
            field === "email" ||
            field === "organizationName" ||
            field === "password"
          ) {
            setError(field, { message });
          }
        });
        setFormError(err.message);
      } else {
        setFormError("Unable to reach the registration server.");
      }
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-bg-app px-4 py-12">
      <div className="w-full max-w-md space-y-6">
        <div className="flex flex-col items-center text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-brand-navy text-brand-gold shadow-sm">
            <Award className="h-6 w-6" />
          </div>
          <h1 className="mt-3 text-2xl font-semibold tracking-tight text-text-main">
            Create Organization Workspace
          </h1>
          <p className="mt-1 text-sm text-text-secondary">
            Register an administrator account and initialize your organization.
          </p>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Organization Registration</CardTitle>
            <CardDescription>
              The creator is assigned the organization ADMIN role automatically.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
              {formError ? (
                <div
                  role="alert"
                  className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700"
                >
                  {formError}
                </div>
              ) : null}

              <div className="space-y-1.5">
                <label htmlFor="organizationName" className="text-sm font-medium text-text-main">
                  Organization Name
                </label>
                <Input
                  id="organizationName"
                  placeholder="Phnom Penh Digital Academy"
                  {...register("organizationName")}
                />
                {errors.organizationName ? (
                  <p className="text-xs text-rose-600">{errors.organizationName.message}</p>
                ) : null}
              </div>

              <div className="space-y-1.5">
                <label htmlFor="fullName" className="text-sm font-medium text-text-main">
                  Administrator Full Name
                </label>
                <Input id="fullName" placeholder="Sokha Example" {...register("fullName")} />
                {errors.fullName ? (
                  <p className="text-xs text-rose-600">{errors.fullName.message}</p>
                ) : null}
              </div>

              <div className="space-y-1.5">
                <label htmlFor="email" className="text-sm font-medium text-text-main">
                  Work Email
                </label>
                <Input
                  id="email"
                  type="email"
                  autoComplete="email"
                  placeholder="admin@academy.org"
                  {...register("email")}
                />
                {errors.email ? (
                  <p className="text-xs text-rose-600">{errors.email.message}</p>
                ) : null}
              </div>

              <div className="space-y-1.5">
                <label htmlFor="password" className="text-sm font-medium text-text-main">
                  Password
                </label>
                <Input
                  id="password"
                  type="password"
                  autoComplete="new-password"
                  placeholder="Minimum 8 characters"
                  {...register("password")}
                />
                {errors.password ? (
                  <p className="text-xs text-rose-600">{errors.password.message}</p>
                ) : null}
              </div>

              <Button type="submit" className="w-full" disabled={isSubmitting}>
                {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                {isSubmitting ? "Creating Workspace..." : "Create Organization"}
              </Button>
            </form>

            <div className="mt-5 flex items-center justify-between border-t border-border-subtle pt-4 text-xs text-text-secondary">
              <Link href="/" className="hover:text-text-main hover:underline">
                Back to Dashboard
              </Link>
              <Link href="/login" className="font-medium text-brand-blue hover:underline">
                Already registered? Sign In
              </Link>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

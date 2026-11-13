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
import { loginAccount } from "@/lib/api/auth";
import { ApiClientError } from "@/lib/api/types";

const loginSchema = z.object({
  email: z.string().email("Enter a valid email address."),
  password: z.string().min(1, "Password is required."),
});

type LoginFormValues = z.infer<typeof loginSchema>;

export default function LoginPage() {
  const router = useRouter();
  const { setAuthenticatedSession } = useAuth();
  const [formError, setFormError] = React.useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  const onSubmit = async (values: LoginFormValues) => {
    setFormError(null);
    try {
      const response = await loginAccount(values);
      setAuthenticatedSession(response.data.session);
      router.push("/");
    } catch (err) {
      if (err instanceof ApiClientError) {
        Object.entries(err.fieldErrors).forEach(([field, message]) => {
          if (field === "email" || field === "password") {
            setError(field, { message });
          }
        });
        setFormError(err.message);
      } else {
        setFormError("Unable to reach the authentication server.");
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
            Smart Certificate Generator
          </h1>
          <p className="mt-1 text-sm text-text-secondary">Sign in to your organization workspace</p>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Sign In</CardTitle>
            <CardDescription>Use your organization credentials to continue.</CardDescription>
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
                <label htmlFor="email" className="text-sm font-medium text-text-main">
                  Email
                </label>
                <Input
                  id="email"
                  type="email"
                  autoComplete="email"
                  placeholder="admin@organization.org"
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
                  autoComplete="current-password"
                  placeholder="••••••••"
                  {...register("password")}
                />
                {errors.password ? (
                  <p className="text-xs text-rose-600">{errors.password.message}</p>
                ) : null}
              </div>

              <Button type="submit" className="w-full" disabled={isSubmitting}>
                {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                {isSubmitting ? "Signing in..." : "Sign In"}
              </Button>
            </form>

            <div className="mt-5 flex items-center justify-between border-t border-border-subtle pt-4 text-xs text-text-secondary">
              <Link href="/" className="hover:text-text-main hover:underline">
                Back to Dashboard
              </Link>
              <Link href="/register" className="font-medium text-brand-blue hover:underline">
                Create Organization Account
              </Link>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

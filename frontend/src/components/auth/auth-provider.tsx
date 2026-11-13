"use client";

import * as React from "react";
import { fetchCurrentSession, logoutAccount } from "@/lib/api/auth";
import type { AuthMe } from "@/lib/api/types";

type AuthStatus = "loading" | "authenticated" | "unauthenticated";

interface AuthContextValue {
  status: AuthStatus;
  session: AuthMe | null;
  setAuthenticatedSession: (session: AuthMe) => void;
  refreshSession: () => Promise<void>;
  logout: () => Promise<void>;
  hasPermission: (code: string) => boolean;
}

const AuthContext = React.createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = React.useState<AuthStatus>("loading");
  const [session, setSession] = React.useState<AuthMe | null>(null);

  const refreshSession = React.useCallback(async () => {
    setStatus("loading");
    try {
      const response = await fetchCurrentSession();
      setSession(response.data);
      setStatus("authenticated");
    } catch {
      setSession(null);
      setStatus("unauthenticated");
    }
  }, []);

  React.useEffect(() => {
    const controller = new AbortController();
    let active = true;

    fetchCurrentSession(controller.signal)
      .then((response) => {
        if (!active) return;
        setSession(response.data);
        setStatus("authenticated");
      })
      .catch(() => {
        if (!active || controller.signal.aborted) return;
        setSession(null);
        setStatus("unauthenticated");
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, []);

  const setAuthenticatedSession = React.useCallback((nextSession: AuthMe) => {
    setSession(nextSession);
    setStatus("authenticated");
  }, []);

  const logout = React.useCallback(async () => {
    await logoutAccount().catch(() => null);
    setSession(null);
    setStatus("unauthenticated");
  }, []);

  const hasPermission = React.useCallback(
    (code: string) => Boolean(session?.permissions.includes(code)),
    [session]
  );

  const value = React.useMemo<AuthContextValue>(
    () => ({
      status,
      session,
      setAuthenticatedSession,
      refreshSession,
      logout,
      hasPermission,
    }),
    [status, session, setAuthenticatedSession, refreshSession, logout, hasPermission]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = React.useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return ctx;
}

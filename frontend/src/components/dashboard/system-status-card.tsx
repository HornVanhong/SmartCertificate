"use client";

import * as React from "react";
import { CheckCircle2, Database, RefreshCw, Server } from "lucide-react";
import { fetchHealthStatus } from "@/lib/api/client";
import { ApiClientError, type HealthStatus } from "@/lib/api/types";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { StatePanel } from "@/components/ui/state-panel";

type LoadState =
  | { status: "loading" }
  | { status: "success"; data: HealthStatus; requestId: string }
  | { status: "error"; message: string };

export function SystemStatusCard() {
  const [state, setState] = React.useState<LoadState>({ status: "loading" });
  const [refreshTick, setRefreshTick] = React.useState(0);

  React.useEffect(() => {
    const controller = new AbortController();
    let active = true;

    fetchHealthStatus(controller.signal)
      .then((response) => {
        if (!active) return;
        setState({
          status: "success",
          data: response.data,
          requestId: response.meta.requestId,
        });
      })
      .catch((err: unknown) => {
        if (!active || (err instanceof DOMException && err.name === "AbortError")) {
          return;
        }
        const message =
          err instanceof ApiClientError
            ? `${err.message} (${err.code})`
            : "Unable to reach the Spring Boot API. Start the backend server to verify live connectivity.";
        setState({ status: "error", message });
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [refreshTick]);

  const handleRefresh = () => {
    setState({ status: "loading" });
    setRefreshTick((tick) => tick + 1);
  };

  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-4">
        <div>
          <CardTitle>Platform Connectivity</CardTitle>
          <CardDescription>
            Live health check against <code className="text-xs">/api/v1/health</code>
          </CardDescription>
        </div>
        <Button
          variant="outline"
          size="sm"
          onClick={handleRefresh}
          disabled={state.status === "loading"}
        >
          <RefreshCw
            className={`h-3.5 w-3.5 ${state.status === "loading" ? "animate-spin" : ""}`}
          />
          Refresh
        </Button>
      </CardHeader>
      <CardContent>
        {state.status === "loading" ? (
          <StatePanel
            variant="loading"
            title="Checking backend services"
            description="Verifying Spring Boot API and PostgreSQL connection..."
          />
        ) : state.status === "error" ? (
          <StatePanel
            variant="error"
            title="Backend unreachable"
            description={state.message}
            actionLabel="Retry Check"
            onAction={handleRefresh}
          />
        ) : (
          <div className="grid gap-3 sm:grid-cols-3">
            <div className="rounded-lg border border-border-subtle bg-bg-app p-3.5">
              <div className="flex items-center justify-between">
                <span className="flex items-center gap-1.5 text-xs font-medium text-text-secondary">
                  <Server className="h-3.5 w-3.5" />
                  API Service
                </span>
                <Badge variant={state.data.status === "UP" ? "success" : "warning"}>
                  <CheckCircle2 className="h-3 w-3" />
                  {state.data.status}
                </Badge>
              </div>
              <p className="mt-2 text-sm font-semibold text-text-main">{state.data.service}</p>
            </div>

            <div className="rounded-lg border border-border-subtle bg-bg-app p-3.5">
              <div className="flex items-center justify-between">
                <span className="flex items-center gap-1.5 text-xs font-medium text-text-secondary">
                  <Database className="h-3.5 w-3.5" />
                  PostgreSQL
                </span>
                <Badge variant={state.data.database === "UP" ? "success" : "danger"}>
                  {state.data.database}
                </Badge>
              </div>
              <p className="mt-2 text-sm font-semibold text-text-main">Flyway Schema Ready</p>
            </div>

            <div className="rounded-lg border border-border-subtle bg-bg-app p-3.5">
              <span className="text-xs font-medium text-text-secondary">Request Trace ID</span>
              <p className="mt-2 truncate font-mono text-xs text-text-main">{state.requestId}</p>
              <p className="mt-1 text-[11px] text-text-secondary">
                {new Date(state.data.timestamp).toLocaleString()}
              </p>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

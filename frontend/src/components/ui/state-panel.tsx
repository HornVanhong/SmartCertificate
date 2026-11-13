import * as React from "react";
import { AlertCircle, FolderOpen, Loader2, RefreshCw } from "lucide-react";
import { Button } from "./button";
import { cn } from "@/lib/utils";

interface StatePanelProps {
  variant: "loading" | "empty" | "error";
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
  className?: string;
  children?: React.ReactNode;
}

export function StatePanel({
  variant,
  title,
  description,
  actionLabel,
  onAction,
  className,
  children,
}: StatePanelProps) {
  const Icon = variant === "loading" ? Loader2 : variant === "error" ? AlertCircle : FolderOpen;

  return (
    <div
      role={variant === "error" ? "alert" : "status"}
      className={cn(
        "flex flex-col items-center justify-center rounded-xl border border-dashed border-border-subtle bg-surface px-6 py-10 text-center",
        className
      )}
    >
      <div
        className={cn(
          "mb-3 flex h-11 w-11 items-center justify-center rounded-full",
          variant === "error" && "bg-rose-50 text-rose-600",
          variant === "loading" && "bg-brand-blue/10 text-brand-blue",
          variant === "empty" && "bg-brand-navy/8 text-brand-navy"
        )}
      >
        <Icon className={cn("h-5 w-5", variant === "loading" && "animate-spin")} />
      </div>
      <h3 className="text-base font-semibold text-text-main">{title}</h3>
      <p className="mt-1 max-w-md text-sm text-text-secondary">{description}</p>
      {children ? <div className="mt-4">{children}</div> : null}
      {actionLabel && onAction ? (
        <Button variant="outline" size="sm" className="mt-4" onClick={onAction}>
          {variant === "error" ? <RefreshCw className="h-3.5 w-3.5" /> : null}
          {actionLabel}
        </Button>
      ) : null}
    </div>
  );
}

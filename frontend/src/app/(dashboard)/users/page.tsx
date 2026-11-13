"use client";

import * as React from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, RefreshCw, UserPlus } from "lucide-react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useAuth } from "@/components/auth/auth-provider";
import { RequireAuthPanel } from "@/components/auth/require-auth-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { StatePanel } from "@/components/ui/state-panel";
import { ApiClientError, type UserInvitation, type UserMembership } from "@/lib/api/types";
import {
  inviteOrganizationUser,
  listOrganizationUsers,
  listUserInvitations,
  updateOrganizationUser,
  updateOrganizationUserStatus,
} from "@/lib/api/users";

const inviteSchema = z.object({
  fullName: z.string().min(1, "Full name is required.").max(160),
  email: z.string().email("Enter a valid email address.").max(254),
  roleName: z.enum(["ADMIN", "GENERAL_USER"]),
});

type InviteFormValues = z.infer<typeof inviteSchema>;

function UsersRolesContent() {
  const { session, hasPermission } = useAuth();
  const canReadUsers = hasPermission("user:read");
  const canManageUsers = hasPermission("user:manage");

  const [members, setMembers] = React.useState<UserMembership[]>([]);
  const [invitations, setInvitations] = React.useState<UserInvitation[]>([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [feedback, setFeedback] = React.useState<{
    type: "success" | "error";
    text: string;
  } | null>(null);
  const [busyUserId, setBusyUserId] = React.useState<string | null>(null);
  const [reloadTick, setReloadTick] = React.useState(0);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<InviteFormValues>({
    resolver: zodResolver(inviteSchema),
    defaultValues: { fullName: "", email: "", roleName: "GENERAL_USER" },
  });

  React.useEffect(() => {
    if (!canReadUsers) {
      return;
    }
    const controller = new AbortController();
    let active = true;

    Promise.all([
      listOrganizationUsers(0, 50, controller.signal),
      listUserInvitations(0, 50, controller.signal),
    ])
      .then(([usersRes, invRes]) => {
        if (!active) return;
        setMembers(usersRes.data);
        setInvitations(invRes.data);
        setError(null);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (!active || (err instanceof DOMException && err.name === "AbortError")) {
          return;
        }
        setError(
          err instanceof ApiClientError ? err.message : "Failed to load organization users."
        );
        setLoading(false);
      });

    return () => {
      active = false;
      controller.abort();
    };
  }, [canReadUsers, reloadTick]);

  const triggerReload = () => {
    setLoading(true);
    setReloadTick((t) => t + 1);
  };

  if (!canReadUsers) {
    return (
      <StatePanel
        variant="error"
        title="Administrator access required"
        description="Your current role does not include user directory permissions."
      />
    );
  }

  const onInviteSubmit = async (values: InviteFormValues) => {
    setFeedback(null);
    try {
      const response = await inviteOrganizationUser(values);
      setInvitations((prev) => [response.data, ...prev]);
      reset();
      setFeedback({
        type: "success",
        text: `Invitation created for ${response.data.email} (${response.data.roleName}).`,
      });
    } catch (err) {
      setFeedback({
        type: "error",
        text: err instanceof ApiClientError ? err.message : "Failed to invite user.",
      });
    }
  };

  const handleRoleChange = async (member: UserMembership, nextRole: "ADMIN" | "GENERAL_USER") => {
    setBusyUserId(member.userId);
    setFeedback(null);
    try {
      const response = await updateOrganizationUser(member.userId, { roleName: nextRole });
      setMembers((prev) => prev.map((m) => (m.userId === member.userId ? response.data : m)));
      setFeedback({ type: "success", text: `Updated role for ${member.fullName} to ${nextRole}.` });
    } catch (err) {
      setFeedback({
        type: "error",
        text: err instanceof ApiClientError ? err.message : "Failed to update role.",
      });
    } finally {
      setBusyUserId(null);
    }
  };

  const handleToggleStatus = async (member: UserMembership) => {
    const nextStatus = member.membershipStatus === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    if (
      nextStatus === "INACTIVE" &&
      !window.confirm(`Deactivate ${member.fullName} (${member.email}) in this organization?`)
    ) {
      return;
    }
    setBusyUserId(member.userId);
    setFeedback(null);
    try {
      const response = await updateOrganizationUserStatus(member.userId, nextStatus);
      setMembers((prev) => prev.map((m) => (m.userId === member.userId ? response.data : m)));
      setFeedback({
        type: "success",
        text: `${member.fullName} is now ${response.data.membershipStatus}.`,
      });
    } catch (err) {
      setFeedback({
        type: "error",
        text: err instanceof ApiClientError ? err.message : "Failed to update membership status.",
      });
    } finally {
      setBusyUserId(null);
    }
  };

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-text-main">
            Users &amp; Roles
          </h1>
          <p className="mt-1 text-sm text-text-secondary">
            Manage members, invitations, and role permissions within{" "}
            <span className="font-medium text-text-main">{session?.organization.name}</span>.
          </p>
        </div>
        <Button variant="outline" size="sm" onClick={triggerReload} disabled={loading}>
          <RefreshCw className={`h-3.5 w-3.5 ${loading ? "animate-spin" : ""}`} />
          Refresh
        </Button>
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

      {canManageUsers ? (
        <Card>
          <CardHeader>
            <CardTitle>Invite Organization Member</CardTitle>
            <CardDescription>
              Create a time-limited invitation for a new administrator or general user.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form
              onSubmit={handleSubmit(onInviteSubmit)}
              className="grid gap-3 sm:grid-cols-4"
              noValidate
            >
              <div>
                <Input placeholder="Full name" {...register("fullName")} />
                {errors.fullName ? (
                  <p className="mt-1 text-xs text-rose-600">{errors.fullName.message}</p>
                ) : null}
              </div>
              <div>
                <Input type="email" placeholder="user@organization.org" {...register("email")} />
                {errors.email ? (
                  <p className="mt-1 text-xs text-rose-600">{errors.email.message}</p>
                ) : null}
              </div>
              <div>
                <select
                  aria-label="Role"
                  {...register("roleName")}
                  className="h-9 w-full rounded-lg border border-border-subtle bg-surface px-3 text-sm text-text-main focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-blue"
                >
                  <option value="GENERAL_USER">GENERAL_USER</option>
                  <option value="ADMIN">ADMIN</option>
                </select>
              </div>
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <UserPlus className="h-4 w-4" />
                )}
                Send Invitation
              </Button>
            </form>
          </CardContent>
        </Card>
      ) : null}

      {loading ? (
        <StatePanel
          variant="loading"
          title="Loading organization directory"
          description="Fetching members and pending invitations..."
        />
      ) : error ? (
        <StatePanel
          variant="error"
          title="Unable to load users"
          description={error}
          actionLabel="Retry"
          onAction={triggerReload}
        />
      ) : (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>Members ({members.length})</CardTitle>
            </CardHeader>
            <CardContent className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-border-subtle text-xs uppercase text-text-secondary">
                    <th className="py-2.5 pr-4">User</th>
                    <th className="py-2.5 pr-4">Role</th>
                    <th className="py-2.5 pr-4">Status</th>
                    <th className="py-2.5 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border-subtle">
                  {members.map((member) => {
                    const isSelf = member.userId === session?.userId;
                    const busy = busyUserId === member.userId;

                    return (
                      <tr key={member.membershipId}>
                        <td className="py-3 pr-4">
                          <p className="font-medium text-text-main">
                            {member.fullName} {isSelf ? "(You)" : ""}
                          </p>
                          <p className="text-xs text-text-secondary">{member.email}</p>
                        </td>
                        <td className="py-3 pr-4">
                          {canManageUsers ? (
                            <select
                              aria-label={`Role for ${member.fullName}`}
                              value={member.roleName}
                              disabled={busy}
                              onChange={(e) =>
                                void handleRoleChange(
                                  member,
                                  e.target.value as "ADMIN" | "GENERAL_USER"
                                )
                              }
                              className="h-8 rounded-md border border-border-subtle bg-surface px-2 text-xs text-text-main"
                            >
                              <option value="ADMIN">ADMIN</option>
                              <option value="GENERAL_USER">GENERAL_USER</option>
                            </select>
                          ) : (
                            <Badge variant="navy">{member.roleName}</Badge>
                          )}
                        </td>
                        <td className="py-3 pr-4">
                          <Badge
                            variant={member.membershipStatus === "ACTIVE" ? "success" : "neutral"}
                          >
                            {member.membershipStatus}
                          </Badge>
                        </td>
                        <td className="py-3 text-right">
                          {canManageUsers && !isSelf ? (
                            <Button
                              variant="outline"
                              size="sm"
                              disabled={busy}
                              onClick={() => void handleToggleStatus(member)}
                            >
                              {member.membershipStatus === "ACTIVE" ? "Deactivate" : "Activate"}
                            </Button>
                          ) : (
                            <span className="text-xs text-text-secondary">—</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Invitations ({invitations.length})</CardTitle>
            </CardHeader>
            <CardContent>
              {invitations.length === 0 ? (
                <p className="text-sm text-text-secondary">No invitations sent yet.</p>
              ) : (
                <div className="divide-y divide-border-subtle">
                  {invitations.map((inv) => (
                    <div
                      key={inv.id}
                      className="flex flex-wrap items-center justify-between gap-2 py-2.5 text-sm"
                    >
                      <div>
                        <p className="font-medium text-text-main">{inv.fullName}</p>
                        <p className="text-xs text-text-secondary">{inv.email}</p>
                      </div>
                      <div className="flex items-center gap-2">
                        <Badge variant="navy">{inv.roleName}</Badge>
                        <Badge variant={inv.status === "PENDING" ? "warning" : "neutral"}>
                          {inv.status}
                        </Badge>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}

export default function UsersRolesPage() {
  return (
    <RequireAuthPanel>
      <UsersRolesContent />
    </RequireAuthPanel>
  );
}

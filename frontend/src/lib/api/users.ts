import { apiRequest } from "./client";
import type { UserInvitation, UserMembership } from "./types";

export interface InviteUserPayload {
  email: string;
  fullName: string;
  roleName: "ADMIN" | "GENERAL_USER";
}

export interface UpdateUserPayload {
  fullName?: string;
  roleName?: "ADMIN" | "GENERAL_USER";
}

export function listOrganizationUsers(page = 0, size = 20, signal?: AbortSignal) {
  return apiRequest<UserMembership[]>(`/users?page=${page}&size=${size}`, {
    method: "GET",
    cache: "no-store",
    signal,
  });
}

export function listUserInvitations(page = 0, size = 20, signal?: AbortSignal) {
  return apiRequest<UserInvitation[]>(`/users/invitations?page=${page}&size=${size}`, {
    method: "GET",
    cache: "no-store",
    signal,
  });
}

export function inviteOrganizationUser(payload: InviteUserPayload) {
  return apiRequest<UserInvitation>("/users/invitations", {
    method: "POST",
    body: payload,
  });
}

export function updateOrganizationUser(userId: string, payload: UpdateUserPayload) {
  return apiRequest<UserMembership>(`/users/${encodeURIComponent(userId)}`, {
    method: "PATCH",
    body: payload,
  });
}

export function updateOrganizationUserStatus(userId: string, status: "ACTIVE" | "INACTIVE") {
  return apiRequest<UserMembership>(`/users/${encodeURIComponent(userId)}/status`, {
    method: "PATCH",
    body: { status },
  });
}

import { apiRequest } from "./client";
import type { AssetType, MediaAsset, OrganizationProfile } from "./types";

export interface UpdateOrganizationPayload {
  name: string;
  logoAssetId?: string | null;
  contactEmail?: string;
  phone?: string;
  address?: string;
  website?: string;
  brandSettings?: {
    primaryColor?: string;
    accentColor?: string;
    defaultIssuerTitle?: string;
  };
}

export function fetchCurrentOrganization(signal?: AbortSignal) {
  return apiRequest<OrganizationProfile>("/organizations/current", {
    method: "GET",
    cache: "no-store",
    signal,
  });
}

export function updateCurrentOrganization(payload: UpdateOrganizationPayload) {
  return apiRequest<OrganizationProfile>("/organizations/current", {
    method: "PUT",
    body: payload,
  });
}

export function listOrganizationAssets(
  params: { assetType?: AssetType; page?: number; size?: number } = {},
  signal?: AbortSignal
) {
  const search = new URLSearchParams();
  if (params.assetType) search.set("assetType", params.assetType);
  if (params.page !== undefined) search.set("page", String(params.page));
  if (params.size !== undefined) search.set("size", String(params.size));
  const query = search.toString();
  return apiRequest<MediaAsset[]>(`/organizations/current/assets${query ? `?${query}` : ""}`, {
    method: "GET",
    cache: "no-store",
    signal,
  });
}

export function uploadOrganizationAsset(file: File, assetType: AssetType) {
  const formData = new FormData();
  formData.append("file", file);
  return apiRequest<MediaAsset>(
    `/organizations/current/assets?assetType=${encodeURIComponent(assetType)}`,
    {
      method: "POST",
      body: formData,
    }
  );
}

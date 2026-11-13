"use client";

import { RequireAuthPanel } from "@/components/auth/require-auth-panel";
import { AssetLibraryManager } from "@/components/organization/asset-library-manager";
import type { AssetType } from "@/lib/api/types";

const BRANDING_TYPES: AssetType[] = ["LOGO", "GENERAL"];

export default function OrganizationBrandingPage() {
  return (
    <RequireAuthPanel>
      <AssetLibraryManager
        title="Branding & Logo"
        description="Upload organization logos and visual branding images stored in private storage."
        allowedTypes={BRANDING_TYPES}
        defaultType="LOGO"
        allowSetAsLogo
      />
    </RequireAuthPanel>
  );
}

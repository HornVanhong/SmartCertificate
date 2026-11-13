"use client";

import { RequireAuthPanel } from "@/components/auth/require-auth-panel";
import { AssetLibraryManager } from "@/components/organization/asset-library-manager";
import type { AssetType } from "@/lib/api/types";

const SIGNATURE_TYPES: AssetType[] = ["SIGNATURE", "STAMP"];

export default function OrganizationSignaturesPage() {
  return (
    <RequireAuthPanel>
      <AssetLibraryManager
        title="Signatures & Stamps"
        description="Upload authorized signatory images and official organization stamps."
        allowedTypes={SIGNATURE_TYPES}
        defaultType="SIGNATURE"
      />
    </RequireAuthPanel>
  );
}

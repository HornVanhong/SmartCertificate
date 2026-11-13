import { PlannedModuleView } from "@/components/dashboard/planned-module-view";

export default function SettingsPage() {
  return (
    <PlannedModuleView
      title="Settings"
      description="Configure certificate numbering prefixes, security preferences, and API integration details."
      phaseLabel="Phase 2+"
      plannedCapabilities={[
        "Session and refresh-token security settings",
        "Certificate numbering format defaults",
        "Supported localization preferences (English & Khmer)",
        "Storage and export configuration",
      ]}
    />
  );
}

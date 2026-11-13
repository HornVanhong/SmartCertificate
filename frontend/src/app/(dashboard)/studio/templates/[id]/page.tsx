"use client";

import { useParams } from "next/navigation";
import { RequireAuthPanel } from "@/components/auth/require-auth-panel";
import { StudioEditorWorkspace } from "@/components/studio/studio-editor-workspace";
import { StatePanel } from "@/components/ui/state-panel";

export default function TemplateStudioPage() {
  const params = useParams<{ id: string }>();
  const templateId = params?.id ?? "";

  return (
    <RequireAuthPanel>
      {templateId ? (
        <StudioEditorWorkspace templateId={templateId} />
      ) : (
        <StatePanel
          variant="error"
          title="Missing template ID"
          description="Select a template from the Template Gallery to open Certificate Studio."
        />
      )}
    </RequireAuthPanel>
  );
}

import { TemplateGalleryView } from "@/components/templates/template-gallery-view";

export default function MyDesignsPage() {
  return (
    <TemplateGalleryView
      mode="drafts"
      title="My Designs"
      description="Iterate on draft certificate designs before publishing immutable versions for issuance."
    />
  );
}

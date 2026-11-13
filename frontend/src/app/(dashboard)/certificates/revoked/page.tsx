import { CertificateListView } from "@/components/certificates/certificate-list-view";

export default function RevokedCertificatesPage() {
  return <CertificateListView mode="revoked" />;
}

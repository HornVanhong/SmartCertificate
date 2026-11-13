export interface PageMeta {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiMeta {
  requestId: string;
  pagination?: PageMeta;
}

export interface ApiResponse<T> {
  data: T;
  meta: ApiMeta;
}

export interface ApiErrorBody {
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
  requestId: string;
}

export interface ApiErrorResponse {
  error: ApiErrorBody;
}

export interface HealthStatus {
  status: "UP" | "DEGRADED" | string;
  service: string;
  database: "UP" | "DOWN" | "NOT_CONFIGURED" | string;
  timestamp: string;
}

export interface OrganizationSummary {
  id: string;
  name: string;
  slug: string;
}

export interface AuthMe {
  userId: string;
  fullName: string;
  email: string;
  organization: OrganizationSummary;
  roleName: string;
  permissions: string[];
}

export interface AuthSession {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  session: AuthMe;
}

export interface OrganizationProfile {
  id: string;
  name: string;
  slug: string;
  logoAssetId: string | null;
  logoUrl: string | null;
  contactEmail: string | null;
  phone: string | null;
  address: string | null;
  website: string | null;
  brandSettings: {
    primaryColor?: string;
    accentColor?: string;
    defaultIssuerTitle?: string;
  };
  createdAt: string;
  updatedAt: string;
}

export type AssetType = "LOGO" | "SIGNATURE" | "STAMP" | "GENERAL";

export interface MediaAsset {
  id: string;
  originalFileName: string;
  mimeType: string;
  fileSize: number;
  assetType: AssetType;
  width: number | null;
  height: number | null;
  signedUrl: string;
  createdAt: string;
}

export interface UserMembership {
  membershipId: string;
  userId: string;
  fullName: string;
  email: string;
  userStatus: string;
  membershipStatus: "ACTIVE" | "INACTIVE" | string;
  roleName: "ADMIN" | "GENERAL_USER" | string;
  createdAt: string;
}

export interface UserInvitation {
  id: string;
  email: string;
  fullName: string;
  roleName: string;
  status: string;
  expiresAt: string;
  createdAt: string;
}

export type TemplateOrientation = "LANDSCAPE" | "PORTRAIT";
export type TemplateStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";
export type DesignElementType = "TEXT" | "IMAGE" | "LOGO" | "SIGNATURE" | "STAMP" | "SHAPE" | "QR";

export interface DesignPage {
  width: number;
  height: number;
  unit: "pt" | "px" | string;
  orientation: TemplateOrientation;
  backgroundColor: string;
  backgroundAssetId?: string | null;
}

export interface DesignElement {
  id: string;
  type: DesignElementType;
  name?: string | null;
  x: number;
  y: number;
  width: number;
  height: number;
  rotation: number;
  opacity: number;
  visible: boolean;
  locked: boolean;
  zIndex: number;
  text?: string | null;
  fontFamily?: string | null;
  fontSize?: number | null;
  fontWeight?: string | null;
  fontStyle?: string | null;
  fill?: string | null;
  textAlign?: string | null;
  lineHeight?: number | null;
  assetId?: string | null;
  fit?: string | null;
  shapeKind?: string | null;
  stroke?: string | null;
  strokeWidth?: number | null;
  cornerRadius?: number | null;
  qrDataTemplate?: string | null;
  foregroundColor?: string | null;
  backgroundColor?: string | null;
}

export interface DesignDocument {
  schemaVersion: string;
  page: DesignPage;
  elements: DesignElement[];
}

export interface TemplateSummary {
  id: string;
  name: string;
  description: string | null;
  category: string | null;
  orientation: TemplateOrientation;
  pageWidth: number;
  pageHeight: number;
  status: TemplateStatus;
  currentVersion: number;
  publishedVersion: number | null;
  previewDesign: DesignDocument | null;
  assetUrls: Record<string, string>;
  createdAt: string;
  updatedAt: string;
}

export interface TemplateVersionSummary {
  id: string;
  versionNumber: number;
  createdBy: string | null;
  createdAt: string;
}

export interface TemplateDetail {
  id: string;
  name: string;
  description: string | null;
  category: string | null;
  orientation: TemplateOrientation;
  pageWidth: number;
  pageHeight: number;
  status: TemplateStatus;
  currentVersion: number;
  publishedVersion: number | null;
  designJson: DesignDocument;
  assetUrls: Record<string, string>;
  versions: TemplateVersionSummary[];
  createdAt: string;
  updatedAt: string;
}

export interface TemplateVersion {
  id: string;
  versionNumber: number;
  designJson: DesignDocument;
  createdBy: string | null;
  createdAt: string;
}

export interface TemplatePreview {
  templateId: string;
  versionNumber: number;
  pageWidth: number;
  pageHeight: number;
  orientation: TemplateOrientation;
  resolvedDesignJson: DesignDocument;
  resolvedPlaceholders: Record<string, string>;
  assetUrls: Record<string, string>;
  warnings: string[];
}

export type CertificateStatus = "PENDING" | "ISSUED" | "REVOKED" | "EXPIRED" | "FAILED";
export type CertificateFormat = "PDF" | "PNG";

export interface CertificateSummary {
  id: string;
  templateId: string;
  templateVersionId: string;
  recipientName: string;
  recipientEmail: string | null;
  courseName: string;
  certificateNumber: string;
  status: CertificateStatus;
  completionDate: string | null;
  issuedAt: string;
  expiresAt: string | null;
  revokedAt: string | null;
  createdBy: string | null;
  createdAt: string;
}

export interface CertificateDetail {
  id: string;
  organizationId: string;
  templateId: string;
  templateVersionId: string;
  recipientName: string;
  recipientEmail: string | null;
  courseName: string;
  certificateNumber: string;
  status: CertificateStatus;
  completionDate: string | null;
  issuedAt: string;
  expiresAt: string | null;
  revokedAt: string | null;
  revokedBy: string | null;
  revokedReason: string | null;
  pdfAvailable: boolean;
  pngAvailable: boolean;
  metadata: Record<string, string>;
  idempotencyKey: string | null;
  createdBy: string | null;
  createdAt: string;
}

export interface CertificateAuditLog {
  id: string;
  certificateId: string | null;
  actorUserId: string | null;
  action: string;
  reason: string | null;
  details: Record<string, unknown>;
  createdAt: string;
}

export type PublicVerificationStatus = "VALID" | "REVOKED" | "EXPIRED";

export interface PublicVerificationResult {
  status: PublicVerificationStatus;
  certificateNumber: string;
  recipientName: string;
  courseName: string;
  organizationName: string;
  issuedAt: string;
  expiresAt: string | null;
  revokedAt: string | null;
}

export type BatchStatus =
  | "UPLOADED"
  | "MAPPED"
  | "VALIDATED"
  | "QUEUED"
  | "PROCESSING"
  | "COMPLETED"
  | "COMPLETED_WITH_ERRORS"
  | "FAILED";

export type BatchItemStatus =
  "PENDING" | "VALID" | "INVALID" | "PROCESSING" | "SUCCEEDED" | "FAILED";

export interface BatchSummary {
  id: string;
  templateId: string;
  templateVersionId: string | null;
  sourceFileName: string;
  status: BatchStatus;
  totalRecords: number;
  validRecords: number;
  invalidRecords: number;
  successfulRecords: number;
  failedRecords: number;
  zipAvailable: boolean;
  createdBy: string;
  createdAt: string;
  completedAt: string | null;
}

export interface BatchDetail {
  id: string;
  organizationId: string;
  templateId: string;
  templateVersionId: string | null;
  sourceFileName: string;
  status: BatchStatus;
  totalRecords: number;
  validRecords: number;
  invalidRecords: number;
  successfulRecords: number;
  failedRecords: number;
  zipAvailable: boolean;
  sourceHeaders: string[];
  columnMapping: Record<string, string>;
  templatePlaceholders: string[];
  sampleRows: Record<string, string>[];
  idempotencyKey: string | null;
  errorSummary: string | null;
  createdBy: string;
  createdAt: string;
  completedAt: string | null;
}

export interface BatchErrorItem {
  id: string;
  rowNumber: number;
  status: BatchItemStatus;
  errorMessage: string | null;
  inputData: Record<string, string>;
}

export type DashboardScope = "ORGANIZATION" | "PERSONAL";

export interface DashboardSummary {
  totalCertificates: number;
  issuedCertificates: number;
  revokedCertificates: number;
  issuedLast30Days: number;
  totalTemplates: number;
  publishedTemplates: number;
  draftTemplates: number;
  totalBatches: number;
  activeBatches: number;
  scope: DashboardScope;
}

export interface IssuanceTrendPoint {
  date: string;
  issuedCount: number;
}

export class ApiClientError extends Error {
  public readonly status: number;
  public readonly code: string;
  public readonly fieldErrors: Record<string, string>;
  public readonly requestId?: string;

  constructor(params: {
    status: number;
    code: string;
    message: string;
    fieldErrors?: Record<string, string>;
    requestId?: string;
  }) {
    super(params.message);
    this.name = "ApiClientError";
    this.status = params.status;
    this.code = params.code;
    this.fieldErrors = params.fieldErrors ?? {};
    this.requestId = params.requestId;
  }
}

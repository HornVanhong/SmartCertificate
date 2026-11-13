package com.smartcertificate.certificate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "certificates")
class CertificateEntity {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "template_version_id", nullable = false)
    private UUID templateVersionId;

    @Column(name = "recipient_name", nullable = false, length = 200)
    private String recipientName;

    @Column(name = "recipient_email", length = 254)
    private String recipientEmail;

    @Column(name = "course_name", nullable = false, length = 200)
    private String courseName;

    @Column(name = "certificate_number", nullable = false, length = 100)
    private String certificateNumber;

    @Column(name = "verification_token_hash", nullable = false, length = 128)
    private String verificationTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CertificateStatus status;

    @Column(name = "completion_date")
    private LocalDate completionDate;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "pdf_object_key")
    private String pdfObjectKey;

    @Column(name = "png_object_key")
    private String pngObjectKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, String> metadata;

    @Column(name = "idempotency_key", length = 160)
    private String idempotencyKey;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revoked_reason")
    private String revokedReason;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CertificateEntity() {}

    CertificateEntity(
            UUID id,
            UUID organizationId,
            UUID templateId,
            UUID templateVersionId,
            String recipientName,
            String recipientEmail,
            String courseName,
            String certificateNumber,
            String verificationTokenHash,
            LocalDate completionDate,
            Instant expiresAt,
            String pdfObjectKey,
            String pngObjectKey,
            Map<String, String> metadata,
            String idempotencyKey,
            UUID createdBy) {
        this.id = id;
        this.organizationId = organizationId;
        this.templateId = templateId;
        this.templateVersionId = templateVersionId;
        this.recipientName = recipientName;
        this.recipientEmail = recipientEmail;
        this.courseName = courseName;
        this.certificateNumber = certificateNumber;
        this.verificationTokenHash = verificationTokenHash;
        this.completionDate = completionDate;
        this.expiresAt = expiresAt;
        this.pdfObjectKey = pdfObjectKey;
        this.pngObjectKey = pngObjectKey;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.idempotencyKey = idempotencyKey;
        this.createdBy = createdBy;
        Instant now = Instant.now();
        this.issuedAt = now;
        this.createdAt = now;
        this.status = CertificateStatus.ISSUED;
    }

    void markRevoked(UUID actorUserId, String reason) {
        this.status = CertificateStatus.REVOKED;
        this.revokedBy = actorUserId;
        this.revokedReason = reason;
        this.revokedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public UUID getTemplateVersionId() {
        return templateVersionId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public String getCourseName() {
        return courseName;
    }

    public String getCertificateNumber() {
        return certificateNumber;
    }

    public String getVerificationTokenHash() {
        return verificationTokenHash;
    }

    public CertificateStatus getStatus() {
        return status;
    }

    public LocalDate getCompletionDate() {
        return completionDate;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getPdfObjectKey() {
        return pdfObjectKey;
    }

    public String getPngObjectKey() {
        return pngObjectKey;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getRevokedBy() {
        return revokedBy;
    }

    public String getRevokedReason() {
        return revokedReason;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

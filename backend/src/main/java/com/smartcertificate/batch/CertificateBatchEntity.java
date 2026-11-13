package com.smartcertificate.batch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "certificate_batches")
class CertificateBatchEntity {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "template_version_id")
    private UUID templateVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BatchStatus status;

    @Column(name = "total_records", nullable = false)
    private int totalRecords;

    @Column(name = "valid_records", nullable = false)
    private int validRecords;

    @Column(name = "invalid_records", nullable = false)
    private int invalidRecords;

    @Column(name = "successful_records", nullable = false)
    private int successfulRecords;

    @Column(name = "failed_records", nullable = false)
    private int failedRecords;

    @Column(name = "source_file_asset_id", nullable = false)
    private UUID sourceFileAssetId;

    @Column(name = "source_file_name", nullable = false, length = 255)
    private String sourceFileName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_headers", nullable = false, columnDefinition = "jsonb")
    private List<String> sourceHeaders;

    @Column(name = "result_zip_object_key")
    private String resultZipObjectKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "column_mapping", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> columnMapping;

    @Column(name = "idempotency_key", length = 160)
    private String idempotencyKey;

    @Column(name = "error_summary")
    private String errorSummary;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected CertificateBatchEntity() {}

    CertificateBatchEntity(
            UUID id,
            UUID organizationId,
            UUID templateId,
            UUID templateVersionId,
            int totalRecords,
            UUID sourceFileAssetId,
            String sourceFileName,
            List<String> sourceHeaders,
            Map<String, String> columnMapping,
            String idempotencyKey,
            UUID createdBy) {
        this.id = id;
        this.organizationId = organizationId;
        this.templateId = templateId;
        this.templateVersionId = templateVersionId;
        this.status = BatchStatus.UPLOADED;
        this.totalRecords = totalRecords;
        this.validRecords = 0;
        this.invalidRecords = 0;
        this.successfulRecords = 0;
        this.failedRecords = 0;
        this.sourceFileAssetId = sourceFileAssetId;
        this.sourceFileName = sourceFileName;
        this.sourceHeaders = sourceHeaders == null ? List.of() : List.copyOf(sourceHeaders);
        this.columnMapping = columnMapping == null ? Map.of() : Map.copyOf(columnMapping);
        this.idempotencyKey = idempotencyKey;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    void updateMapping(Map<String, String> newMapping) {
        this.columnMapping = newMapping == null ? Map.of() : Map.copyOf(newMapping);
        this.status = BatchStatus.MAPPED;
        this.validRecords = 0;
        this.invalidRecords = 0;
        this.errorSummary = null;
    }

    void markValidated(int validCount, int invalidCount) {
        this.validRecords = validCount;
        this.invalidRecords = invalidCount;
        this.status = BatchStatus.VALIDATED;
        this.errorSummary = null;
    }

    void markQueued(UUID templateVersionId) {
        this.templateVersionId = templateVersionId;
        this.status = BatchStatus.QUEUED;
        this.completedAt = null;
        this.errorSummary = null;
    }

    void markProcessing() {
        this.status = BatchStatus.PROCESSING;
        this.errorSummary = null;
    }

    void updateProgress(int succeededCount, int failedCount) {
        this.successfulRecords = succeededCount;
        this.failedRecords = failedCount;
    }

    void markFinished(
            BatchStatus terminalStatus, int succeededCount, int failedCount, String zipObjectKey, String errorSummary) {
        this.status = terminalStatus;
        this.successfulRecords = succeededCount;
        this.failedRecords = failedCount;
        this.resultZipObjectKey = zipObjectKey;
        this.errorSummary = errorSummary;
        this.completedAt = Instant.now();
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

    public BatchStatus getStatus() {
        return status;
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    public int getValidRecords() {
        return validRecords;
    }

    public int getInvalidRecords() {
        return invalidRecords;
    }

    public int getSuccessfulRecords() {
        return successfulRecords;
    }

    public int getFailedRecords() {
        return failedRecords;
    }

    public UUID getSourceFileAssetId() {
        return sourceFileAssetId;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public List<String> getSourceHeaders() {
        return sourceHeaders;
    }

    public String getResultZipObjectKey() {
        return resultZipObjectKey;
    }

    public Map<String, String> getColumnMapping() {
        return columnMapping;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}

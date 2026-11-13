package com.smartcertificate.batch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "certificate_batch_items")
class CertificateBatchItemEntity {

    @Id
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> inputData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BatchItemStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "certificate_id")
    private UUID certificateId;

    @Column(name = "idempotency_key", nullable = false, length = 160)
    private String idempotencyKey;

    protected CertificateBatchItemEntity() {}

    CertificateBatchItemEntity(UUID id, UUID batchId, int rowNumber, Map<String, String> inputData) {
        this.id = id;
        this.batchId = batchId;
        this.rowNumber = rowNumber;
        this.inputData = inputData == null ? Map.of() : Map.copyOf(inputData);
        this.status = BatchItemStatus.PENDING;
        this.idempotencyKey = "batch:" + batchId + ":row:" + rowNumber;
    }

    void resetToPending() {
        if (this.status != BatchItemStatus.SUCCEEDED) {
            this.status = BatchItemStatus.PENDING;
            this.errorMessage = null;
        }
    }

    void markValid() {
        if (this.status != BatchItemStatus.SUCCEEDED) {
            this.status = BatchItemStatus.VALID;
            this.errorMessage = null;
        }
    }

    void markInvalid(String errorMessage) {
        this.status = BatchItemStatus.INVALID;
        this.errorMessage = errorMessage;
    }

    void markProcessing() {
        this.status = BatchItemStatus.PROCESSING;
        this.errorMessage = null;
    }

    void markSucceeded(UUID certificateId) {
        this.status = BatchItemStatus.SUCCEEDED;
        this.certificateId = certificateId;
        this.errorMessage = null;
    }

    void markFailed(String errorMessage) {
        this.status = BatchItemStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    void markForRetry() {
        if (this.status == BatchItemStatus.FAILED) {
            this.status = BatchItemStatus.VALID;
            this.errorMessage = null;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public Map<String, String> getInputData() {
        return inputData;
    }

    public BatchItemStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public UUID getCertificateId() {
        return certificateId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}

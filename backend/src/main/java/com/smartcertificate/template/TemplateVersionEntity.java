package com.smartcertificate.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "template_versions")
class TemplateVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "design_json", nullable = false, columnDefinition = "jsonb")
    private DesignDocument designJson;

    @Column(name = "preview_asset_id")
    private UUID previewAssetId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TemplateVersionEntity() {}

    TemplateVersionEntity(UUID templateId, int versionNumber, DesignDocument designJson, UUID createdBy) {
        this.templateId = templateId;
        this.versionNumber = versionNumber;
        this.designJson = designJson;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public DesignDocument getDesignJson() {
        return designJson;
    }

    public UUID getPreviewAssetId() {
        return previewAssetId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    void updateDraftDesign(DesignDocument designJson) {
        this.designJson = designJson;
    }
}

package com.smartcertificate.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "templates")
class TemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 180)
    private String name;

    private String description;

    @Column(length = 80)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TemplateOrientation orientation;

    @Column(name = "page_width", nullable = false, precision = 10, scale = 2)
    private BigDecimal pageWidth;

    @Column(name = "page_height", nullable = false, precision = 10, scale = 2)
    private BigDecimal pageHeight;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TemplateStatus status;

    @Column(name = "current_version", nullable = false)
    private int currentVersion;

    @Column(name = "published_version")
    private Integer publishedVersion;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TemplateEntity() {}

    TemplateEntity(
            UUID organizationId,
            String name,
            String description,
            String category,
            TemplateOrientation orientation,
            BigDecimal pageWidth,
            BigDecimal pageHeight,
            UUID createdBy) {
        this.organizationId = organizationId;
        this.name = name;
        this.description = description;
        this.category = category;
        this.orientation = orientation;
        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
        this.status = TemplateStatus.DRAFT;
        this.currentVersion = 1;
        this.publishedVersion = null;
        this.createdBy = createdBy;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public TemplateOrientation getOrientation() {
        return orientation;
    }

    public BigDecimal getPageWidth() {
        return pageWidth;
    }

    public BigDecimal getPageHeight() {
        return pageHeight;
    }

    public TemplateStatus getStatus() {
        return status;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public Integer getPublishedVersion() {
        return publishedVersion;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    void updateDraftMetadata(
            String name,
            String description,
            String category,
            TemplateOrientation orientation,
            BigDecimal pageWidth,
            BigDecimal pageHeight,
            int nextVersion) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.orientation = orientation;
        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
        this.currentVersion = nextVersion;
        this.status = TemplateStatus.DRAFT;
        this.updatedAt = Instant.now();
    }

    void markPublished() {
        this.status = TemplateStatus.PUBLISHED;
        this.publishedVersion = this.currentVersion;
        this.updatedAt = Instant.now();
    }

    void markArchived() {
        this.status = TemplateStatus.ARCHIVED;
        this.updatedAt = Instant.now();
    }
}

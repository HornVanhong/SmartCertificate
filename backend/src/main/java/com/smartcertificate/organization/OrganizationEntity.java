package com.smartcertificate.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "organizations")
class OrganizationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "logo_asset_id")
    private UUID logoAssetId;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(length = 50)
    private String phone;

    private String address;
    private String website;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "brand_settings", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> brandSettings;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrganizationEntity() {}

    OrganizationEntity(String name, String slug, String contactEmail) {
        this.name = name;
        this.slug = slug;
        this.contactEmail = contactEmail;
        this.brandSettings = Map.of("primaryColor", "#142D4E", "accentColor", "#C6A45B");
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public UUID getLogoAssetId() {
        return logoAssetId;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    public String getWebsite() {
        return website;
    }

    public Map<String, Object> getBrandSettings() {
        return brandSettings;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    void updateProfile(
            String name,
            UUID logoAssetId,
            String contactEmail,
            String phone,
            String address,
            String website,
            Map<String, Object> brandSettings) {
        this.name = name;
        this.logoAssetId = logoAssetId;
        this.contactEmail = contactEmail;
        this.phone = phone;
        this.address = address;
        this.website = website;
        this.brandSettings = brandSettings == null ? Map.of() : Map.copyOf(brandSettings);
        this.updatedAt = Instant.now();
    }
}

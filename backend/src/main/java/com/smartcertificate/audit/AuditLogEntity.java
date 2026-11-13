package com.smartcertificate.audit;

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
@Table(name = "certificate_audit_logs")
class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "certificate_id")
    private UUID certificateId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(nullable = false, length = 80)
    private String action;

    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLogEntity() {}

    AuditLogEntity(
            UUID organizationId,
            UUID certificateId,
            UUID actorUserId,
            String action,
            String reason,
            Map<String, Object> details) {
        this.organizationId = organizationId;
        this.certificateId = certificateId;
        this.actorUserId = actorUserId;
        this.action = action;
        this.reason = reason;
        this.details = details == null ? Map.of() : Map.copyOf(details);
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCertificateId() {
        return certificateId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getAction() {
        return action;
    }

    public String getReason() {
        return reason;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

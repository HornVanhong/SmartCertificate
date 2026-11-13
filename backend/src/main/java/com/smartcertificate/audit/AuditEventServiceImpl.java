package com.smartcertificate.audit;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuditEventServiceImpl implements AuditEventService {

    private final AuditLogRepository auditLogRepository;

    AuditEventServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional
    public void recordEvent(
            UUID organizationId, UUID actorUserId, String action, String reason, Map<String, Object> details) {
        recordCertificateEvent(organizationId, null, actorUserId, action, reason, details);
    }

    @Override
    @Transactional
    public void recordCertificateEvent(
            UUID organizationId,
            UUID certificateId,
            UUID actorUserId,
            String action,
            String reason,
            Map<String, Object> details) {
        AuditLogEntity entity = new AuditLogEntity(organizationId, certificateId, actorUserId, action, reason, details);
        auditLogRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> listCertificateAuditLogs(UUID organizationId, UUID certificateId, Pageable pageable) {
        return auditLogRepository
                .findByOrganizationIdAndCertificateIdOrderByCreatedAtDesc(organizationId, certificateId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> listOrganizationAuditLogs(
            UUID organizationId, UUID actorUserIdFilter, String action, String query, Pageable pageable) {
        String normalizedAction =
                action == null || action.isBlank() ? null : action.trim().toUpperCase(Locale.ROOT);
        String normalizedQuery =
                query == null || query.isBlank() ? null : query.trim().toLowerCase(Locale.ROOT);

        Specification<AuditLogEntity> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), organizationId));
            if (actorUserIdFilter != null) {
                predicates.add(cb.equal(root.get("actorUserId"), actorUserIdFilter));
            }
            if (normalizedAction != null) {
                predicates.add(cb.equal(cb.upper(root.get("action")), normalizedAction));
            }
            if (normalizedQuery != null) {
                String pattern = "%" + normalizedQuery + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("action")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("reason"), "")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));

        return auditLogRepository.findAll(spec, sorted).map(this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLogEntity entity) {
        return new AuditLogResponse(
                entity.getId(),
                entity.getCertificateId(),
                entity.getActorUserId(),
                entity.getAction(),
                entity.getReason(),
                entity.getDetails(),
                entity.getCreatedAt());
    }
}

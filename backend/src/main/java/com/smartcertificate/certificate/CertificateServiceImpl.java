package com.smartcertificate.certificate;

import com.smartcertificate.audit.AuditEventService;
import com.smartcertificate.common.config.VerificationProperties;
import com.smartcertificate.common.exception.AppException;
import com.smartcertificate.common.exception.ErrorCode;
import com.smartcertificate.common.util.TokenHashingUtil;
import com.smartcertificate.media.StorageService;
import com.smartcertificate.organization.OrganizationService;
import com.smartcertificate.template.TemplateService;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CertificateServiceImpl implements CertificateService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    private final CertificateRepository certificateRepository;
    private final TemplateService templateService;
    private final OrganizationService organizationService;
    private final CertificatePdfRenderer pdfRenderer;
    private final StorageService storageService;
    private final AuditEventService auditEventService;
    private final VerificationProperties verificationProperties;

    CertificateServiceImpl(
            CertificateRepository certificateRepository,
            TemplateService templateService,
            OrganizationService organizationService,
            CertificatePdfRenderer pdfRenderer,
            StorageService storageService,
            AuditEventService auditEventService,
            VerificationProperties verificationProperties) {
        this.certificateRepository = certificateRepository;
        this.templateService = templateService;
        this.organizationService = organizationService;
        this.pdfRenderer = pdfRenderer;
        this.storageService = storageService;
        this.auditEventService = auditEventService;
        this.verificationProperties = verificationProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CertificateSummaryResponse> listCertificates(
            UUID organizationId,
            UUID actorUserId,
            boolean canReadAll,
            String query,
            CertificateStatus status,
            UUID templateId,
            Pageable pageable) {
        String normalizedQuery = trimToNull(query);

        Specification<CertificateEntity> spec = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizationId"), organizationId));
            if (!canReadAll) {
                predicates.add(cb.equal(root.get("createdBy"), actorUserId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (templateId != null) {
                predicates.add(cb.equal(root.get("templateId"), templateId));
            }
            if (normalizedQuery != null) {
                String pattern = "%" + normalizedQuery.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("recipientName")), pattern),
                        cb.like(cb.lower(root.get("courseName")), pattern),
                        cb.like(cb.lower(root.get("certificateNumber")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        Pageable sorted = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));

        return certificateRepository.findAll(spec, sorted).map(this::toSummary);
    }

    @Override
    @Transactional
    public IssueOutcome issueCertificate(UUID organizationId, UUID actorUserId, IssueCertificateRequest request) {
        String normalizedKey = trimToNull(request.idempotencyKey());
        if (normalizedKey != null) {
            var existing = certificateRepository.findByOrganizationIdAndIdempotencyKey(organizationId, normalizedKey);
            if (existing.isPresent()) {
                return new IssueOutcome(toDetail(existing.get()), true);
            }
        }

        if (request.expiresAt() != null && request.expiresAt().isBefore(Instant.now())) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Expiration date must be in the future.",
                    Map.of("expiresAt", "Expiration timestamp must be in the future."));
        }

        TemplateService.PublishedTemplateSnapshot templateSnapshot =
                templateService.getPublishedTemplateForIssuance(organizationId, request.templateId());
        OrganizationService.OrganizationSummary org = organizationService.getSummary(organizationId);

        long sequence = certificateRepository.nextCertificateSequence();
        int year = ZonedDateTime.now(ZoneOffset.UTC).getYear();
        String certificateNumber = String.format("SC-%d-%06d", year, sequence);

        String rawVerificationToken = TokenHashingUtil.generateOpaqueToken(32);
        String tokenHash = TokenHashingUtil.sha256Hex(rawVerificationToken);
        String verificationUrl = buildVerificationUrl(rawVerificationToken);

        Map<String, String> sanitizedMetadata = sanitizeMetadata(request.metadata());
        Map<String, String> placeholders =
                buildIssuancePlaceholders(request, org.name(), certificateNumber, verificationUrl, sanitizedMetadata);

        CertificatePdfRenderer.RenderedCertificate rendered =
                pdfRenderer.render(templateSnapshot.designJson(), organizationId, placeholders);

        UUID certificateId = UUID.randomUUID();
        String pdfObjectKey = "orgs/" + organizationId + "/certificates/" + certificateId + "/certificate.pdf";
        String pngObjectKey = "orgs/" + organizationId + "/certificates/" + certificateId + "/certificate.png";

        storageService.store(pdfObjectKey, rendered.pdfBytes());
        storageService.store(pngObjectKey, rendered.pngBytes());

        try {
            CertificateEntity saved = certificateRepository.save(new CertificateEntity(
                    certificateId,
                    organizationId,
                    templateSnapshot.templateId(),
                    templateSnapshot.templateVersionId(),
                    request.recipientName().trim(),
                    trimToNull(request.recipientEmail()),
                    request.courseName().trim(),
                    certificateNumber,
                    tokenHash,
                    request.completionDate(),
                    request.expiresAt(),
                    pdfObjectKey,
                    pngObjectKey,
                    sanitizedMetadata,
                    normalizedKey,
                    actorUserId));

            auditEventService.recordCertificateEvent(
                    organizationId,
                    saved.getId(),
                    actorUserId,
                    "CERTIFICATE_ISSUED",
                    null,
                    Map.of(
                            "certificateNumber", certificateNumber,
                            "templateId", templateSnapshot.templateId().toString(),
                            "templateVersionId",
                                    templateSnapshot.templateVersionId().toString(),
                            "templateVersion", templateSnapshot.versionNumber()));

            return new IssueOutcome(toDetail(saved), false);
        } catch (DataIntegrityViolationException ex) {
            storageService.deleteIfExists(pdfObjectKey);
            storageService.deleteIfExists(pngObjectKey);
            if (normalizedKey != null) {
                var raceWinner =
                        certificateRepository.findByOrganizationIdAndIdempotencyKey(organizationId, normalizedKey);
                if (raceWinner.isPresent()) {
                    return new IssueOutcome(toDetail(raceWinner.get()), true);
                }
            }
            throw ex;
        } catch (RuntimeException ex) {
            storageService.deleteIfExists(pdfObjectKey);
            storageService.deleteIfExists(pngObjectKey);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateDetailResponse getCertificate(
            UUID organizationId, UUID actorUserId, boolean canReadAll, UUID certificateId) {
        CertificateEntity cert = requireAccessibleCertificate(organizationId, actorUserId, canReadAll, certificateId);
        return toDetail(cert);
    }

    @Override
    @Transactional
    public DownloadPayload downloadCertificate(
            UUID organizationId, UUID actorUserId, boolean canReadAll, UUID certificateId, CertificateFormat format) {
        CertificateEntity cert = requireAccessibleCertificate(organizationId, actorUserId, canReadAll, certificateId);
        String objectKey = format == CertificateFormat.PNG ? cert.getPngObjectKey() : cert.getPdfObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            throw new AppException(ErrorCode.NOT_FOUND, "Requested certificate file format is not available.");
        }
        byte[] content = storageService.load(objectKey);
        String ext = format == CertificateFormat.PNG ? "png" : "pdf";
        String contentType = format == CertificateFormat.PNG ? "image/png" : "application/pdf";

        auditEventService.recordCertificateEvent(
                organizationId,
                cert.getId(),
                actorUserId,
                "CERTIFICATE_DOWNLOADED",
                null,
                Map.of("format", format.name(), "certificateNumber", cert.getCertificateNumber()));

        return new DownloadPayload(cert.getCertificateNumber() + "." + ext, contentType, content);
    }

    @Override
    @Transactional
    public CertificateDetailResponse revokeCertificate(
            UUID organizationId, UUID actorUserId, UUID certificateId, RevokeCertificateRequest request) {
        CertificateEntity cert = certificateRepository
                .findByIdAndOrganizationId(certificateId, organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Certificate was not found."));

        if (cert.getStatus() == CertificateStatus.REVOKED) {
            throw new AppException(ErrorCode.CONFLICT, "Certificate is already revoked.");
        }

        String trimmedReason = request.reason().trim();
        cert.markRevoked(actorUserId, trimmedReason);
        CertificateEntity saved = certificateRepository.save(cert);

        auditEventService.recordCertificateEvent(
                organizationId,
                saved.getId(),
                actorUserId,
                "CERTIFICATE_REVOKED",
                trimmedReason,
                Map.of("certificateNumber", saved.getCertificateNumber()));

        return toDetail(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditEventService.AuditLogResponse> listCertificateAuditLogs(
            UUID organizationId, UUID actorUserId, boolean canReadAll, UUID certificateId, Pageable pageable) {
        requireAccessibleCertificate(organizationId, actorUserId, canReadAll, certificateId);
        return auditEventService.listCertificateAuditLogs(organizationId, certificateId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublicCertificateSnapshot> findPublicSnapshotByTokenHash(String tokenHash) {
        if (tokenHash == null || tokenHash.isBlank()) {
            return Optional.empty();
        }
        return certificateRepository
                .findByVerificationTokenHash(tokenHash)
                .map(cert -> new PublicCertificateSnapshot(
                        cert.getId(),
                        cert.getOrganizationId(),
                        cert.getStatus(),
                        cert.getCertificateNumber(),
                        cert.getRecipientName(),
                        cert.getCourseName(),
                        cert.getIssuedAt(),
                        cert.getExpiresAt(),
                        cert.getRevokedAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CertificatePdfPayload> loadCertificatePdfForBatch(UUID organizationId, UUID certificateId) {
        return certificateRepository
                .findByIdAndOrganizationId(certificateId, organizationId)
                .filter(cert -> cert.getPdfObjectKey() != null
                        && !cert.getPdfObjectKey().isBlank())
                .map(cert -> new CertificatePdfPayload(
                        cert.getCertificateNumber(),
                        cert.getRecipientName(),
                        storageService.load(cert.getPdfObjectKey())));
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateStats getCertificateStats(UUID organizationId, UUID actorUserId, boolean canReadAll) {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        if (canReadAll) {
            return new CertificateStats(
                    certificateRepository.countByOrganizationId(organizationId),
                    certificateRepository.countByOrganizationIdAndStatus(organizationId, CertificateStatus.ISSUED),
                    certificateRepository.countByOrganizationIdAndStatus(organizationId, CertificateStatus.REVOKED),
                    certificateRepository.countByOrganizationIdAndIssuedAtAfter(organizationId, thirtyDaysAgo));
        }
        return new CertificateStats(
                certificateRepository.countByOrganizationIdAndCreatedBy(organizationId, actorUserId),
                certificateRepository.countByOrganizationIdAndCreatedByAndStatus(
                        organizationId, actorUserId, CertificateStatus.ISSUED),
                certificateRepository.countByOrganizationIdAndCreatedByAndStatus(
                        organizationId, actorUserId, CertificateStatus.REVOKED),
                certificateRepository.countByOrganizationIdAndCreatedByAndIssuedAtAfter(
                        organizationId, actorUserId, thirtyDaysAgo));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyIssuanceCount> getDailyIssuanceTrend(
            UUID organizationId, UUID actorUserId, boolean canReadAll, int days) {
        int clampedDays = Math.min(Math.max(days, 7), 90);
        LocalDate endDate = LocalDate.now(ZoneOffset.UTC);
        LocalDate startDate = endDate.minusDays(clampedDays - 1L);
        Instant since = startDate.atStartOfDay().toInstant(ZoneOffset.UTC);

        List<Instant> timestamps = canReadAll
                ? certificateRepository.findIssuedTimestampsSince(organizationId, since)
                : certificateRepository.findCreatorIssuedTimestampsSince(organizationId, actorUserId, since);

        Map<LocalDate, Long> countsByDay = new LinkedHashMap<>();
        for (int i = 0; i < clampedDays; i++) {
            countsByDay.put(startDate.plusDays(i), 0L);
        }
        for (Instant ts : timestamps) {
            if (ts == null) continue;
            LocalDate day = ts.atZone(ZoneOffset.UTC).toLocalDate();
            if (countsByDay.containsKey(day)) {
                countsByDay.put(day, countsByDay.get(day) + 1L);
            }
        }

        return countsByDay.entrySet().stream()
                .map(entry -> new DailyIssuanceCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private CertificateEntity requireAccessibleCertificate(
            UUID organizationId, UUID actorUserId, boolean canReadAll, UUID certificateId) {
        CertificateEntity cert = certificateRepository
                .findByIdAndOrganizationId(certificateId, organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Certificate was not found."));

        if (!canReadAll && !cert.getCreatedBy().equals(actorUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "You do not have permission to view this certificate.");
        }
        return cert;
    }

    private String buildVerificationUrl(String rawToken) {
        String baseUrl = verificationProperties.publicBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:3000";
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBase + "/verify?token=" + rawToken;
    }

    private Map<String, String> buildIssuancePlaceholders(
            IssueCertificateRequest request,
            String orgName,
            String certificateNumber,
            String verificationUrl,
            Map<String, String> metadata) {
        Map<String, String> values = new LinkedHashMap<>(metadata);
        values.put("recipientName", request.recipientName().trim());
        values.put("courseName", request.courseName().trim());
        values.put("organizationName", orgName != null && !orgName.isBlank() ? orgName : "Smart Certificate");
        values.put("certificateNumber", certificateNumber);
        values.put("verificationUrl", verificationUrl);
        String issuedDateStr = ZonedDateTime.now(ZoneOffset.UTC).format(DISPLAY_DATE);
        values.put("issuedDate", issuedDateStr);
        if (request.completionDate() != null) {
            values.put("completionDate", request.completionDate().format(DISPLAY_DATE));
        } else {
            values.putIfAbsent("completionDate", issuedDateStr);
        }
        values.putIfAbsent("issuerName", orgName != null && !orgName.isBlank() ? orgName : "Authorized Signatory");
        return values;
    }

    private Map<String, String> sanitizeMetadata(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        int count = 0;
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            if (count >= 30) break;
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) continue;
            cleaned.put(entry.getKey().trim(), entry.getValue().trim());
            count++;
        }
        return cleaned;
    }

    private CertificateSummaryResponse toSummary(CertificateEntity cert) {
        return new CertificateSummaryResponse(
                cert.getId(),
                cert.getTemplateId(),
                cert.getTemplateVersionId(),
                cert.getRecipientName(),
                cert.getRecipientEmail(),
                cert.getCourseName(),
                cert.getCertificateNumber(),
                cert.getStatus(),
                cert.getCompletionDate(),
                cert.getIssuedAt(),
                cert.getExpiresAt(),
                cert.getRevokedAt(),
                cert.getCreatedBy(),
                cert.getCreatedAt());
    }

    private CertificateDetailResponse toDetail(CertificateEntity cert) {
        return new CertificateDetailResponse(
                cert.getId(),
                cert.getOrganizationId(),
                cert.getTemplateId(),
                cert.getTemplateVersionId(),
                cert.getRecipientName(),
                cert.getRecipientEmail(),
                cert.getCourseName(),
                cert.getCertificateNumber(),
                cert.getStatus(),
                cert.getCompletionDate(),
                cert.getIssuedAt(),
                cert.getExpiresAt(),
                cert.getRevokedAt(),
                cert.getRevokedBy(),
                cert.getRevokedReason(),
                cert.getPdfObjectKey() != null,
                cert.getPngObjectKey() != null,
                cert.getMetadata(),
                cert.getIdempotencyKey(),
                cert.getCreatedBy(),
                cert.getCreatedAt());
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

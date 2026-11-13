package com.smartcertificate.verification;

import com.smartcertificate.certificate.CertificateService;
import com.smartcertificate.certificate.CertificateStatus;
import com.smartcertificate.common.exception.AppException;
import com.smartcertificate.common.exception.ErrorCode;
import com.smartcertificate.common.util.TokenHashingUtil;
import com.smartcertificate.organization.OrganizationService;
import java.time.Instant;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PublicVerificationServiceImpl implements PublicVerificationService {

    private static final Pattern VALID_TOKEN_SHAPE = Pattern.compile("^[A-Za-z0-9_-]{16,256}$");
    private static final String GENERIC_NOT_FOUND_MESSAGE = "Certificate verification record was not found.";

    private final CertificateService certificateService;
    private final OrganizationService organizationService;

    PublicVerificationServiceImpl(CertificateService certificateService, OrganizationService organizationService) {
        this.certificateService = certificateService;
        this.organizationService = organizationService;
    }

    @Override
    @Transactional(readOnly = true)
    public PublicVerificationResponse verifyByToken(String rawToken) {
        String normalized = rawToken == null ? "" : rawToken.trim();
        if (!VALID_TOKEN_SHAPE.matcher(normalized).matches()) {
            throw new AppException(ErrorCode.NOT_FOUND, GENERIC_NOT_FOUND_MESSAGE);
        }

        String tokenHash = TokenHashingUtil.sha256Hex(normalized);
        CertificateService.PublicCertificateSnapshot snapshot = certificateService
                .findPublicSnapshotByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, GENERIC_NOT_FOUND_MESSAGE));

        PublicVerificationStatus publicStatus = resolvePublicStatus(snapshot);
        String orgName =
                organizationService.getSummary(snapshot.organizationId()).name();

        return new PublicVerificationResponse(
                publicStatus,
                snapshot.certificateNumber(),
                snapshot.recipientName(),
                snapshot.courseName(),
                orgName,
                snapshot.issuedAt(),
                snapshot.expiresAt(),
                publicStatus == PublicVerificationStatus.REVOKED ? snapshot.revokedAt() : null);
    }

    private PublicVerificationStatus resolvePublicStatus(CertificateService.PublicCertificateSnapshot snapshot) {
        if (snapshot.status() == CertificateStatus.REVOKED) {
            return PublicVerificationStatus.REVOKED;
        }
        if (snapshot.status() == CertificateStatus.EXPIRED
                || (snapshot.expiresAt() != null && snapshot.expiresAt().isBefore(Instant.now()))) {
            return PublicVerificationStatus.EXPIRED;
        }
        if (snapshot.status() == CertificateStatus.ISSUED) {
            return PublicVerificationStatus.VALID;
        }
        throw new AppException(ErrorCode.NOT_FOUND, GENERIC_NOT_FOUND_MESSAGE);
    }
}

package com.smartcertificate.auth;

import com.smartcertificate.common.config.SecurityProperties;
import com.smartcertificate.common.exception.AppException;
import com.smartcertificate.common.exception.ErrorCode;
import com.smartcertificate.common.util.TokenHashingUtil;
import com.smartcertificate.organization.OrganizationService;
import com.smartcertificate.user.UserAccountService;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserAccountService userAccountService;
    private final OrganizationService organizationService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecurityProperties securityProperties;

    AuthServiceImpl(
            UserAccountService userAccountService,
            OrganizationService organizationService,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SecurityProperties securityProperties) {
        this.userAccountService = userAccountService;
        this.organizationService = organizationService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.securityProperties = securityProperties;
    }

    @Override
    @Transactional
    public AuthSessionResponse register(RegisterRequest request) {
        if (!securityProperties.selfRegistrationEnabled()) {
            throw new AppException(ErrorCode.FORBIDDEN, "Self-registration is disabled. Contact your administrator.");
        }
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (userAccountService.existsByEmail(normalizedEmail)) {
            throw new AppException(
                    ErrorCode.CONFLICT,
                    "An account with this email already exists.",
                    Map.of("email", "Email is already registered."));
        }

        OrganizationService.OrganizationSummary org =
                organizationService.createOrganization(request.organizationName(), normalizedEmail);

        String passwordHash = passwordEncoder.encode(request.password());
        UserAccountService.UserAuthRecord user = userAccountService.createActiveUserWithMembership(
                request.fullName(), normalizedEmail, passwordHash, org.id(), "ADMIN");

        UserAccountService.MembershipAuthRecord membership = userAccountService
                .loadActiveMembership(user.userId(), org.id())
                .orElseThrow(() -> new IllegalStateException("Newly created membership missing"));

        return issueSession(membership, org);
    }

    @Override
    @Transactional
    public AuthSessionResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        UserAccountService.UserAuthRecord user = userAccountService
                .findUserByEmail(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Invalid email or password."));

        if (!"ACTIVE".equals(user.status()) || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Invalid email or password.");
        }

        UserAccountService.MembershipAuthRecord membership =
                userAccountService.loadFirstActiveMembership(user.userId());
        OrganizationService.OrganizationSummary org = organizationService.getSummary(membership.organizationId());

        return issueSession(membership, org);
    }

    @Override
    @Transactional(noRollbackFor = AppException.class)
    public AuthSessionResponse refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Refresh token is required.");
        }

        String tokenHash = TokenHashingUtil.sha256Hex(rawRefreshToken);
        RefreshTokenEntity existing = refreshTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED, "Refresh token is invalid."));

        if (existing.isRevoked()) {
            // Reuse of a rotated/revoked refresh token indicates potential token theft: revoke all sessions.
            refreshTokenRepository.revokeAllActiveByUserId(existing.getUserId(), Instant.now());
            log.warn("Detected refresh token reuse for userId={}, revoked all active sessions", existing.getUserId());
            throw new AppException(ErrorCode.UNAUTHORIZED, "Refresh token has already been used or revoked.");
        }

        if (existing.isExpired()) {
            existing.revoke();
            refreshTokenRepository.save(existing);
            throw new AppException(ErrorCode.UNAUTHORIZED, "Refresh token has expired.");
        }

        existing.revoke();
        refreshTokenRepository.save(existing);

        UserAccountService.MembershipAuthRecord membership =
                userAccountService.loadFirstActiveMembership(existing.getUserId());
        OrganizationService.OrganizationSummary org = organizationService.getSummary(membership.organizationId());

        return issueSession(membership, org);
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String tokenHash = TokenHashingUtil.sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.revoke();
            refreshTokenRepository.save(token);
        });
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        userAccountService.findUserByEmail(normalizedEmail).ifPresent(user -> {
            if (!"ACTIVE".equals(user.status())) {
                return;
            }
            String rawToken = TokenHashingUtil.generateOpaqueToken(32);
            String tokenHash = TokenHashingUtil.sha256Hex(rawToken);
            long ttl = securityProperties.passwordResetTtlSeconds() > 0
                    ? securityProperties.passwordResetTtlSeconds()
                    : 3600L;
            passwordResetTokenRepository.save(new PasswordResetTokenEntity(
                    user.userId(), tokenHash, Instant.now().plusSeconds(ttl)));
            log.info("Password reset token generated for userId={}", user.userId());
        });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String tokenHash = TokenHashingUtil.sha256Hex(request.token());
        PasswordResetTokenEntity resetToken = passwordResetTokenRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.VALIDATION_ERROR, "Password reset token is invalid."));

        if (resetToken.isUsed() || resetToken.isExpired()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Password reset token has expired or been used.");
        }

        resetToken.markUsed();
        passwordResetTokenRepository.save(resetToken);

        userAccountService.updatePasswordHash(resetToken.getUserId(), passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.revokeAllActiveByUserId(resetToken.getUserId(), Instant.now());
    }

    @Override
    @Transactional
    public AuthSessionResponse acceptInvitation(AcceptInvitationRequest request) {
        String passwordHash = passwordEncoder.encode(request.password());
        UserAccountService.MembershipAuthRecord membership =
                userAccountService.acceptInvitation(request.token(), request.fullName(), passwordHash);
        OrganizationService.OrganizationSummary org = organizationService.getSummary(membership.organizationId());
        return issueSession(membership, org);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthMeResponse getCurrentSession(UUID userId, UUID organizationId) {
        UserAccountService.MembershipAuthRecord membership = userAccountService
                .loadActiveMembership(userId, organizationId)
                .orElseThrow(() ->
                        new AppException(ErrorCode.UNAUTHORIZED, "Active organization membership was not found."));
        OrganizationService.OrganizationSummary org = organizationService.getSummary(membership.organizationId());
        return toAuthMe(membership, org);
    }

    private AuthSessionResponse issueSession(
            UserAccountService.MembershipAuthRecord membership, OrganizationService.OrganizationSummary org) {
        String accessToken =
                jwtService.generateAccessToken(membership.userId(), membership.organizationId(), membership.email());

        String rawRefreshToken = TokenHashingUtil.generateOpaqueToken(48);
        String refreshHash = TokenHashingUtil.sha256Hex(rawRefreshToken);
        long refreshTtl =
                securityProperties.refreshTokenTtlSeconds() > 0 ? securityProperties.refreshTokenTtlSeconds() : 604800L;
        refreshTokenRepository.save(new RefreshTokenEntity(
                membership.userId(), refreshHash, Instant.now().plusSeconds(refreshTtl)));

        return new AuthSessionResponse(
                accessToken, rawRefreshToken, jwtService.getAccessTokenTtlSeconds(), toAuthMe(membership, org));
    }

    private AuthMeResponse toAuthMe(
            UserAccountService.MembershipAuthRecord membership, OrganizationService.OrganizationSummary org) {
        return new AuthMeResponse(
                membership.userId(),
                membership.fullName(),
                membership.email(),
                new AuthMeResponse.OrganizationSummaryDto(org.id(), org.name(), org.slug()),
                membership.roleName(),
                membership.permissions());
    }
}

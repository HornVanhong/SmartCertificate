package com.smartcertificate.user;

import com.smartcertificate.audit.AuditEventService;
import com.smartcertificate.common.config.SecurityProperties;
import com.smartcertificate.common.exception.AppException;
import com.smartcertificate.common.exception.ErrorCode;
import com.smartcertificate.common.util.TokenHashingUtil;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserServiceImpl implements UserAccountService, UserManagementService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final UserInvitationRepository userInvitationRepository;
    private final AuditEventService auditEventService;
    private final SecurityProperties securityProperties;

    UserServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserOrganizationRepository userOrganizationRepository,
            UserInvitationRepository userInvitationRepository,
            AuditEventService auditEventService,
            SecurityProperties securityProperties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userOrganizationRepository = userOrganizationRepository;
        this.userInvitationRepository = userInvitationRepository;
        this.auditEventService = auditEventService;
        this.securityProperties = securityProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmailIgnoreCase(email);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserAuthRecord> findUserByEmail(String email) {
        return userRepository
                .findByEmailIgnoreCase(email)
                .map(u -> new UserAuthRecord(
                        u.getId(), u.getFullName(), u.getEmail(), u.getPasswordHash(), u.getStatus()));
    }

    @Override
    @Transactional
    public UserAuthRecord createActiveUserWithMembership(
            String fullName, String email, String passwordHash, UUID organizationId, String roleName) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new AppException(
                    ErrorCode.CONFLICT,
                    "An account with this email already exists.",
                    Map.of("email", "Email is already registered."));
        }
        RoleEntity role = requireSystemRole(roleName);
        UserEntity user = userRepository.save(new UserEntity(fullName.trim(), normalizedEmail, passwordHash, "ACTIVE"));
        userOrganizationRepository.save(new UserOrganizationEntity(user, organizationId, role, "ACTIVE"));

        auditEventService.recordEvent(
                organizationId, user.getId(), "USER_REGISTERED", null, Map.of("role", role.getName()));

        return new UserAuthRecord(
                user.getId(), user.getFullName(), user.getEmail(), user.getPasswordHash(), user.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MembershipAuthRecord> loadActiveMembership(UUID userId, UUID organizationId) {
        return userOrganizationRepository
                .findByUserIdAndOrganizationId(userId, organizationId)
                .filter(m -> "ACTIVE".equals(m.getStatus())
                        && "ACTIVE".equals(m.getUser().getStatus()))
                .map(this::toMembershipAuthRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public MembershipAuthRecord loadFirstActiveMembership(UUID userId) {
        List<UserOrganizationEntity> memberships =
                userOrganizationRepository.findByUserIdAndStatusOrderByCreatedAtAsc(userId, "ACTIVE");
        return memberships.stream()
                .filter(m -> "ACTIVE".equals(m.getUser().getStatus()))
                .findFirst()
                .map(this::toMembershipAuthRecord)
                .orElseThrow(() -> new AppException(
                        ErrorCode.FORBIDDEN, "No active organization membership exists for this account."));
    }

    @Override
    @Transactional
    public void updatePasswordHash(UUID userId, String newPasswordHash) {
        UserEntity user = userRepository
                .findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "User was not found."));
        user.updatePasswordHash(newPasswordHash);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public MembershipAuthRecord acceptInvitation(String rawInvitationToken, String fullName, String passwordHash) {
        String tokenHash = TokenHashingUtil.sha256Hex(rawInvitationToken);
        UserInvitationEntity invitation = userInvitationRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Invitation token is invalid."));

        if (!"PENDING".equals(invitation.getStatus())) {
            throw new AppException(ErrorCode.CONFLICT, "Invitation has already been used or revoked.");
        }
        if (Instant.now().isAfter(invitation.getExpiresAt())) {
            invitation.markExpired();
            userInvitationRepository.save(invitation);
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invitation token has expired.");
        }

        UserEntity user =
                userRepository.findByEmailIgnoreCase(invitation.getEmail()).orElse(null);
        String resolvedName = (fullName != null && !fullName.isBlank()) ? fullName.trim() : invitation.getFullName();

        if (user == null) {
            user = userRepository.save(new UserEntity(
                    resolvedName, invitation.getEmail().toLowerCase(Locale.ROOT), passwordHash, "ACTIVE"));
        } else if (!"ACTIVE".equals(user.getStatus())) {
            user.activate(resolvedName, passwordHash);
            user = userRepository.save(user);
        }

        if (userOrganizationRepository.existsByUserIdAndOrganizationId(user.getId(), invitation.getOrganizationId())) {
            throw new AppException(ErrorCode.CONFLICT, "User is already a member of this organization.");
        }

        UserOrganizationEntity membership = userOrganizationRepository.save(
                new UserOrganizationEntity(user, invitation.getOrganizationId(), invitation.getRole(), "ACTIVE"));
        invitation.markAccepted();
        userInvitationRepository.save(invitation);

        auditEventService.recordEvent(
                invitation.getOrganizationId(),
                user.getId(),
                "USER_INVITATION_ACCEPTED",
                null,
                Map.of(
                        "invitationId",
                        invitation.getId().toString(),
                        "role",
                        invitation.getRole().getName()));

        return toMembershipAuthRecord(membership);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserMembershipResponse> listOrganizationUsers(UUID organizationId, Pageable pageable) {
        return userOrganizationRepository
                .findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable)
                .map(this::toMembershipResponse);
    }

    @Override
    @Transactional
    public InvitationResponse inviteUser(UUID organizationId, UUID actorUserId, InviteUserRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        RoleEntity role = requireSystemRole(request.roleName());

        UserEntity existingUser =
                userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (existingUser != null
                && userOrganizationRepository.existsByUserIdAndOrganizationId(existingUser.getId(), organizationId)) {
            throw new AppException(
                    ErrorCode.CONFLICT,
                    "This user is already a member of your organization.",
                    Map.of("email", "User is already a member."));
        }
        if (userInvitationRepository.existsPendingInvitation(organizationId, normalizedEmail)) {
            throw new AppException(
                    ErrorCode.CONFLICT,
                    "A pending invitation already exists for this email.",
                    Map.of("email", "Pending invitation already exists."));
        }

        String rawToken = TokenHashingUtil.generateOpaqueToken(32);
        String tokenHash = TokenHashingUtil.sha256Hex(rawToken);
        long ttl = securityProperties.invitationTtlSeconds() > 0 ? securityProperties.invitationTtlSeconds() : 604800L;

        UserInvitationEntity invitation = userInvitationRepository.save(new UserInvitationEntity(
                organizationId,
                normalizedEmail,
                request.fullName().trim(),
                role,
                tokenHash,
                actorUserId,
                Instant.now().plusSeconds(ttl)));

        auditEventService.recordEvent(
                organizationId,
                actorUserId,
                "USER_INVITED",
                null,
                Map.of("invitationId", invitation.getId().toString(), "role", role.getName()));

        log.info(
                "Created organization invitation id={} orgId={} role={}",
                invitation.getId(),
                organizationId,
                role.getName());

        return toInvitationResponse(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InvitationResponse> listInvitations(UUID organizationId, Pageable pageable) {
        return userInvitationRepository
                .findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable)
                .map(this::toInvitationResponse);
    }

    @Override
    @Transactional
    public UserMembershipResponse updateUser(
            UUID organizationId, UUID actorUserId, UUID targetUserId, UpdateUserRequest request) {
        UserOrganizationEntity membership = userOrganizationRepository
                .findByUserIdAndOrganizationId(targetUserId, organizationId)
                .orElseThrow(() ->
                        new AppException(ErrorCode.NOT_FOUND, "User membership was not found in your organization."));

        if (request.fullName() != null && !request.fullName().isBlank()) {
            membership.getUser().updateFullName(request.fullName().trim());
        }

        if (request.roleName() != null
                && !request.roleName().equals(membership.getRole().getName())) {
            if ("ADMIN".equals(membership.getRole().getName())
                    && "ACTIVE".equals(membership.getStatus())
                    && userOrganizationRepository.countActiveAdmins(organizationId) <= 1) {
                throw new AppException(ErrorCode.CONFLICT, "Cannot demote the last active organization administrator.");
            }
            RoleEntity nextRole = requireSystemRole(request.roleName());
            membership.updateRole(nextRole);
        }

        UserOrganizationEntity saved = userOrganizationRepository.save(membership);
        auditEventService.recordEvent(
                organizationId,
                actorUserId,
                "USER_UPDATED",
                null,
                Map.of(
                        "targetUserId", targetUserId.toString(),
                        "role", saved.getRole().getName()));

        return toMembershipResponse(saved);
    }

    @Override
    @Transactional
    public UserMembershipResponse updateUserStatus(
            UUID organizationId, UUID actorUserId, UUID targetUserId, UpdateUserStatusRequest request) {
        UserOrganizationEntity membership = userOrganizationRepository
                .findByUserIdAndOrganizationId(targetUserId, organizationId)
                .orElseThrow(() ->
                        new AppException(ErrorCode.NOT_FOUND, "User membership was not found in your organization."));

        if ("INACTIVE".equals(request.status())) {
            if (targetUserId.equals(actorUserId)) {
                throw new AppException(ErrorCode.CONFLICT, "You cannot deactivate your own active membership.");
            }
            if ("ADMIN".equals(membership.getRole().getName())
                    && "ACTIVE".equals(membership.getStatus())
                    && userOrganizationRepository.countActiveAdmins(organizationId) <= 1) {
                throw new AppException(
                        ErrorCode.CONFLICT, "Cannot deactivate the last active organization administrator.");
            }
        }

        membership.updateStatus(request.status());
        UserOrganizationEntity saved = userOrganizationRepository.save(membership);

        auditEventService.recordEvent(
                organizationId,
                actorUserId,
                "USER_STATUS_CHANGED",
                null,
                Map.of("targetUserId", targetUserId.toString(), "status", request.status()));

        return toMembershipResponse(saved);
    }

    private RoleEntity requireSystemRole(String roleName) {
        return roleRepository
                .findByNameAndSystemTrue(roleName)
                .orElseThrow(() -> new AppException(
                        ErrorCode.VALIDATION_ERROR,
                        "Unsupported role specified.",
                        Map.of("roleName", "Role does not exist.")));
    }

    private MembershipAuthRecord toMembershipAuthRecord(UserOrganizationEntity membership) {
        Set<String> permissionCodes = membership.getRole().getPermissions().stream()
                .map(PermissionEntity::getCode)
                .collect(Collectors.toUnmodifiableSet());
        return new MembershipAuthRecord(
                membership.getUser().getId(),
                membership.getOrganizationId(),
                membership.getUser().getEmail(),
                membership.getUser().getFullName(),
                membership.getRole().getName(),
                permissionCodes);
    }

    private UserMembershipResponse toMembershipResponse(UserOrganizationEntity m) {
        return new UserMembershipResponse(
                m.getId(),
                m.getUser().getId(),
                m.getUser().getFullName(),
                m.getUser().getEmail(),
                m.getUser().getStatus(),
                m.getStatus(),
                m.getRole().getName(),
                m.getCreatedAt());
    }

    private InvitationResponse toInvitationResponse(UserInvitationEntity inv) {
        return new InvitationResponse(
                inv.getId(),
                inv.getEmail(),
                inv.getFullName(),
                inv.getRole().getName(),
                inv.getStatus(),
                inv.getExpiresAt(),
                inv.getCreatedAt());
    }
}

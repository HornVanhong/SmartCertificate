package com.smartcertificate.user;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserAccountService {

    record UserAuthRecord(UUID userId, String fullName, String email, String passwordHash, String status) {}

    record MembershipAuthRecord(
            UUID userId,
            UUID organizationId,
            String email,
            String fullName,
            String roleName,
            Set<String> permissions) {}

    boolean existsByEmail(String email);

    Optional<UserAuthRecord> findUserByEmail(String email);

    UserAuthRecord createActiveUserWithMembership(
            String fullName, String email, String passwordHash, UUID organizationId, String roleName);

    Optional<MembershipAuthRecord> loadActiveMembership(UUID userId, UUID organizationId);

    MembershipAuthRecord loadFirstActiveMembership(UUID userId);

    void updatePasswordHash(UUID userId, String newPasswordHash);

    MembershipAuthRecord acceptInvitation(String rawInvitationToken, String fullName, String passwordHash);
}

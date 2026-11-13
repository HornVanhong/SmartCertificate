package com.smartcertificate.user;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserManagementService {

    Page<UserMembershipResponse> listOrganizationUsers(UUID organizationId, Pageable pageable);

    InvitationResponse inviteUser(UUID organizationId, UUID actorUserId, InviteUserRequest request);

    Page<InvitationResponse> listInvitations(UUID organizationId, Pageable pageable);

    UserMembershipResponse updateUser(
            UUID organizationId, UUID actorUserId, UUID targetUserId, UpdateUserRequest request);

    UserMembershipResponse updateUserStatus(
            UUID organizationId, UUID actorUserId, UUID targetUserId, UpdateUserStatusRequest request);
}

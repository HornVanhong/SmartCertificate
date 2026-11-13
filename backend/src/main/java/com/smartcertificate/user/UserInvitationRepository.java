package com.smartcertificate.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserInvitationRepository extends JpaRepository<UserInvitationEntity, UUID> {

    @EntityGraph(attributePaths = {"role"})
    Optional<UserInvitationEntity> findByTokenHash(String tokenHash);

    @EntityGraph(attributePaths = {"role"})
    Page<UserInvitationEntity> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    @Query(
            """
            SELECT COUNT(i) > 0
            FROM UserInvitationEntity i
            WHERE i.organizationId = :orgId
              AND LOWER(i.email) = LOWER(:email)
              AND i.status = 'PENDING'
            """)
    boolean existsPendingInvitation(@Param("orgId") UUID organizationId, @Param("email") String email);
}

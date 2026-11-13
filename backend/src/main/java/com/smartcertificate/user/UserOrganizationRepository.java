package com.smartcertificate.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserOrganizationRepository extends JpaRepository<UserOrganizationEntity, UUID> {

    @EntityGraph(attributePaths = {"user", "role", "role.permissions"})
    Optional<UserOrganizationEntity> findByUserIdAndOrganizationId(UUID userId, UUID organizationId);

    @EntityGraph(attributePaths = {"user", "role", "role.permissions"})
    List<UserOrganizationEntity> findByUserIdAndStatusOrderByCreatedAtAsc(UUID userId, String status);

    @EntityGraph(attributePaths = {"user", "role"})
    Page<UserOrganizationEntity> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    @Query(
            """
            SELECT COUNT(uo)
            FROM UserOrganizationEntity uo
            WHERE uo.organizationId = :orgId
              AND uo.status = 'ACTIVE'
              AND uo.role.name = 'ADMIN'
            """)
    long countActiveAdmins(@Param("orgId") UUID organizationId);

    boolean existsByUserIdAndOrganizationId(UUID userId, UUID organizationId);
}

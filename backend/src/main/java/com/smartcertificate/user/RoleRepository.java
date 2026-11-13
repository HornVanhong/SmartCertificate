package com.smartcertificate.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

    Optional<RoleEntity> findByNameAndSystemTrue(String name);
}

package com.smartcertificate.media;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface MediaAssetRepository extends JpaRepository<MediaAssetEntity, UUID> {

    Page<MediaAssetEntity> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    Page<MediaAssetEntity> findByOrganizationIdAndAssetTypeOrderByCreatedAtDesc(
            UUID organizationId, AssetType assetType, Pageable pageable);

    Optional<MediaAssetEntity> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByIdAndOrganizationId(UUID id, UUID organizationId);
}

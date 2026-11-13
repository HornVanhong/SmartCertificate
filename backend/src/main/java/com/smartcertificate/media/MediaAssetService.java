package com.smartcertificate.media;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface MediaAssetService {

    record AssetContent(String fileName, String mimeType, byte[] bytes) {}

    MediaAssetResponse uploadOrganizationAsset(
            UUID organizationId, UUID actorUserId, AssetType assetType, MultipartFile file);

    Page<MediaAssetResponse> listOrganizationAssets(UUID organizationId, AssetType assetType, Pageable pageable);

    void assertAssetBelongsToOrganization(UUID assetId, UUID organizationId);

    String buildSignedContentUrl(UUID assetId, UUID organizationId);

    AssetContent loadSignedOrAuthorizedContent(
            UUID assetId, UUID authenticatedOrgId, Long expiresEpochSeconds, String signature);

    byte[] loadOrganizationAssetBytes(UUID assetId, UUID organizationId);

    UUID storeBatchSourceFile(
            UUID organizationId, UUID actorUserId, String originalFileName, String mimeType, byte[] bytes);
}

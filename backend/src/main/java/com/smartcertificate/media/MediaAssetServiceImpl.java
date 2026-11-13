package com.smartcertificate.media;

import com.smartcertificate.audit.AuditEventService;
import com.smartcertificate.common.exception.AppException;
import com.smartcertificate.common.exception.ErrorCode;
import com.smartcertificate.common.util.TokenHashingUtil;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
class MediaAssetServiceImpl implements MediaAssetService {

    private final MediaAssetRepository mediaAssetRepository;
    private final AssetValidationService assetValidationService;
    private final StorageService storageService;
    private final SignedUrlService signedUrlService;
    private final AuditEventService auditEventService;

    MediaAssetServiceImpl(
            MediaAssetRepository mediaAssetRepository,
            AssetValidationService assetValidationService,
            StorageService storageService,
            SignedUrlService signedUrlService,
            AuditEventService auditEventService) {
        this.mediaAssetRepository = mediaAssetRepository;
        this.assetValidationService = assetValidationService;
        this.storageService = storageService;
        this.signedUrlService = signedUrlService;
        this.auditEventService = auditEventService;
    }

    @Override
    @Transactional
    public MediaAssetResponse uploadOrganizationAsset(
            UUID organizationId, UUID actorUserId, AssetType assetType, MultipartFile file) {
        AssetValidationService.ValidatedImage validated = assetValidationService.validateImage(file);
        AssetType effectiveType = assetType != null ? assetType : AssetType.GENERAL;
        String checksum = TokenHashingUtil.sha256Hex(validated.bytes());
        String objectKey = "orgs/" + organizationId + "/assets/" + UUID.randomUUID() + "." + validated.extension();

        storageService.store(objectKey, validated.bytes());
        try {
            MediaAssetEntity entity = new MediaAssetEntity(
                    organizationId,
                    objectKey,
                    validated.sanitizedFileName(),
                    validated.mimeType(),
                    validated.bytes().length,
                    checksum,
                    effectiveType,
                    validated.width(),
                    validated.height(),
                    actorUserId);
            MediaAssetEntity saved = mediaAssetRepository.save(entity);

            auditEventService.recordEvent(
                    organizationId,
                    actorUserId,
                    "ASSET_UPLOADED",
                    null,
                    Map.of(
                            "assetId", saved.getId().toString(),
                            "assetType", effectiveType.name(),
                            "mimeType", validated.mimeType(),
                            "fileSize", validated.bytes().length));

            return toResponse(saved);
        } catch (RuntimeException ex) {
            storageService.deleteIfExists(objectKey);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MediaAssetResponse> listOrganizationAssets(
            UUID organizationId, AssetType assetType, Pageable pageable) {
        Page<MediaAssetEntity> page = assetType == null
                ? mediaAssetRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable)
                : mediaAssetRepository.findByOrganizationIdAndAssetTypeOrderByCreatedAtDesc(
                        organizationId, assetType, pageable);
        return page.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public void assertAssetBelongsToOrganization(UUID assetId, UUID organizationId) {
        if (!mediaAssetRepository.existsByIdAndOrganizationId(assetId, organizationId)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Referenced media asset was not found in your organization.");
        }
    }

    @Override
    public String buildSignedContentUrl(UUID assetId, UUID organizationId) {
        return signedUrlService.createAssetContentUrl(assetId, organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public AssetContent loadSignedOrAuthorizedContent(
            UUID assetId, UUID authenticatedOrgId, Long expiresEpochSeconds, String signature) {
        if (expiresEpochSeconds != null && signature != null) {
            MediaAssetEntity asset = mediaAssetRepository
                    .findById(assetId)
                    .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Asset was not found."));
            if (!signedUrlService.isValidSignature(
                    asset.getId(), asset.getOrganizationId(), expiresEpochSeconds, signature)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Signed asset URL is invalid or expired.");
            }
            byte[] content = storageService.load(asset.getObjectKey());
            return new AssetContent(asset.getOriginalFileName(), asset.getMimeType(), content);
        }

        if (authenticatedOrgId == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        }
        MediaAssetEntity asset = mediaAssetRepository
                .findByIdAndOrganizationId(assetId, authenticatedOrgId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Asset was not found."));
        byte[] content = storageService.load(asset.getObjectKey());
        return new AssetContent(asset.getOriginalFileName(), asset.getMimeType(), content);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] loadOrganizationAssetBytes(UUID assetId, UUID organizationId) {
        MediaAssetEntity asset = mediaAssetRepository
                .findByIdAndOrganizationId(assetId, organizationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Referenced media asset was not found."));
        return storageService.load(asset.getObjectKey());
    }

    @Override
    @Transactional
    public UUID storeBatchSourceFile(
            UUID organizationId, UUID actorUserId, String originalFileName, String mimeType, byte[] bytes) {
        String cleanName = sanitizeFileName(originalFileName);
        String ext = cleanName.contains(".")
                ? cleanName.substring(cleanName.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT)
                : "bin";
        String checksum = TokenHashingUtil.sha256Hex(bytes);
        String objectKey = "orgs/" + organizationId + "/batches/sources/" + UUID.randomUUID() + "." + ext;

        storageService.store(objectKey, bytes);
        try {
            MediaAssetEntity entity = new MediaAssetEntity(
                    organizationId,
                    objectKey,
                    cleanName,
                    mimeType,
                    bytes.length,
                    checksum,
                    AssetType.GENERAL,
                    null,
                    null,
                    actorUserId);
            MediaAssetEntity saved = mediaAssetRepository.save(entity);
            auditEventService.recordEvent(
                    organizationId,
                    actorUserId,
                    "BATCH_SOURCE_UPLOADED",
                    null,
                    Map.of(
                            "assetId",
                            saved.getId().toString(),
                            "fileName",
                            cleanName,
                            "mimeType",
                            mimeType,
                            "fileSize",
                            bytes.length));
            return saved.getId();
        } catch (RuntimeException ex) {
            storageService.deleteIfExists(objectKey);
            throw ex;
        }
    }

    private static String sanitizeFileName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "roster.csv";
        }
        String base = rawName.replace("\\", "/");
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        String sanitized = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? "roster.csv" : sanitized.substring(0, Math.min(sanitized.length(), 180));
    }

    private MediaAssetResponse toResponse(MediaAssetEntity entity) {
        return new MediaAssetResponse(
                entity.getId(),
                entity.getOriginalFileName(),
                entity.getMimeType(),
                entity.getFileSize(),
                entity.getAssetType(),
                entity.getWidth(),
                entity.getHeight(),
                signedUrlService.createAssetContentUrl(entity.getId(), entity.getOrganizationId()),
                entity.getCreatedAt());
    }
}

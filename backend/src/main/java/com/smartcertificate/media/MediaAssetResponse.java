package com.smartcertificate.media;

import java.time.Instant;
import java.util.UUID;

public record MediaAssetResponse(
        UUID id,
        String originalFileName,
        String mimeType,
        long fileSize,
        AssetType assetType,
        Integer width,
        Integer height,
        String signedUrl,
        Instant createdAt) {}

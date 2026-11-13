package com.smartcertificate.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String type,
        String localRoot,
        String signedUrlSecret,
        long signedUrlTtlSeconds,
        long maxAssetSizeBytes,
        String s3Bucket,
        String s3Region,
        String s3Prefix,
        String s3Endpoint,
        String s3KmsKeyId) {

    public static StorageProperties ofLocal(
            String localRoot, String signedUrlSecret, long signedUrlTtlSeconds, long maxAssetSizeBytes) {
        return new StorageProperties(
                "local",
                localRoot,
                signedUrlSecret,
                signedUrlTtlSeconds,
                maxAssetSizeBytes,
                null,
                null,
                null,
                null,
                null);
    }
}

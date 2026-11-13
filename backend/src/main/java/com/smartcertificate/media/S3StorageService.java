package com.smartcertificate.media;

import com.smartcertificate.common.config.StorageProperties;
import com.smartcertificate.common.exception.AppException;
import com.smartcertificate.common.exception.ErrorCode;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * Production AWS S3 storage backend with server-side encryption and configurable KMS/prefix.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String prefix;
    private final String kmsKeyId;

    @Autowired
    public S3StorageService(StorageProperties properties) {
        this(properties, createDefaultClient(properties));
    }

    public S3StorageService(StorageProperties properties, S3Client s3Client) {
        if (properties.s3Bucket() == null || properties.s3Bucket().isBlank()) {
            throw new IllegalStateException("app.storage.s3-bucket must be configured when storage type is 's3'.");
        }
        this.bucket = properties.s3Bucket();
        this.prefix = normalizePrefix(properties.s3Prefix());
        this.kmsKeyId = properties.s3KmsKeyId();
        this.s3Client = s3Client;
    }

    private static S3Client createDefaultClient(StorageProperties properties) {
        S3ClientBuilder builder = S3Client.builder();
        if (properties.s3Region() != null && !properties.s3Region().isBlank()) {
            builder.region(Region.of(properties.s3Region().trim()));
        }
        if (properties.s3Endpoint() != null && !properties.s3Endpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.s3Endpoint().trim()));
            builder.forcePathStyle(true);
        }
        return builder.build();
    }

    @Override
    public void store(String objectKey, byte[] content) {
        String fullKey = resolveFullKey(objectKey);
        try {
            PutObjectRequest.Builder requestBuilder =
                    PutObjectRequest.builder().bucket(bucket).key(fullKey).contentLength((long) content.length);

            if (kmsKeyId != null && !kmsKeyId.isBlank()) {
                requestBuilder
                        .serverSideEncryption(ServerSideEncryption.AWS_KMS)
                        .ssekmsKeyId(kmsKeyId);
            } else {
                requestBuilder.serverSideEncryption(ServerSideEncryption.AES256);
            }

            s3Client.putObject(requestBuilder.build(), RequestBody.fromBytes(content));
        } catch (S3Exception ex) {
            throw new IllegalStateException(
                    "Failed to persist media object in S3: "
                            + ex.awsErrorDetails().errorMessage(),
                    ex);
        }
    }

    @Override
    public byte[] load(String objectKey) {
        String fullKey = resolveFullKey(objectKey);
        try {
            GetObjectRequest request =
                    GetObjectRequest.builder().bucket(bucket).key(fullKey).build();
            return s3Client.getObjectAsBytes(request).asByteArray();
        } catch (NoSuchKeyException ex) {
            throw new AppException(ErrorCode.NOT_FOUND, "Stored asset file was not found.");
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new AppException(ErrorCode.NOT_FOUND, "Stored asset file was not found.");
            }
            throw new IllegalStateException(
                    "Failed to read media object from S3: "
                            + ex.awsErrorDetails().errorMessage(),
                    ex);
        }
    }

    @Override
    public void deleteIfExists(String objectKey) {
        String fullKey = resolveFullKey(objectKey);
        try {
            DeleteObjectRequest request =
                    DeleteObjectRequest.builder().bucket(bucket).key(fullKey).build();
            s3Client.deleteObject(request);
        } catch (S3Exception ex) {
            throw new IllegalStateException(
                    "Failed to delete media object from S3: "
                            + ex.awsErrorDetails().errorMessage(),
                    ex);
        }
    }

    private String resolveFullKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Object key is invalid.");
        }
        String clean = objectKey.startsWith("/") ? objectKey.substring(1) : objectKey;
        return prefix.isEmpty() ? clean : prefix + "/" + clean;
    }

    private static String normalizePrefix(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}

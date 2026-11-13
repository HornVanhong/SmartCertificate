package com.smartcertificate.media;

import com.smartcertificate.common.config.StorageProperties;
import com.smartcertificate.common.exception.AppException;
import com.smartcertificate.common.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Local private filesystem storage backend for non-S3 development environments. */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
class LocalFileStorageService implements StorageService {

    private final Path rootPath;

    LocalFileStorageService(StorageProperties properties) {
        this.rootPath = Paths.get(properties.localRoot()).toAbsolutePath().normalize();
    }

    @Override
    public void store(String objectKey, byte[] content) {
        Path target = resolveSafePath(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to persist media object", ex);
        }
    }

    @Override
    public byte[] load(String objectKey) {
        Path target = resolveSafePath(objectKey);
        if (!Files.exists(target)) {
            throw new AppException(ErrorCode.NOT_FOUND, "Stored asset file was not found.");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read media object", ex);
        }
    }

    @Override
    public void deleteIfExists(String objectKey) {
        Path target = resolveSafePath(objectKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete media object", ex);
        }
    }

    private Path resolveSafePath(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Object key is invalid.");
        }
        Path resolved = rootPath.resolve(objectKey).normalize();
        if (!resolved.startsWith(rootPath)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid storage object key.");
        }
        return resolved;
    }
}

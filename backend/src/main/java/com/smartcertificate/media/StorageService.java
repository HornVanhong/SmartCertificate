package com.smartcertificate.media;

public interface StorageService {

    void store(String objectKey, byte[] content);

    byte[] load(String objectKey);

    void deleteIfExists(String objectKey);
}

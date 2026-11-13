package com.smartcertificate.media;

import com.smartcertificate.common.config.StorageProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class SignedUrlService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] secretBytes;
    private final long defaultTtlSeconds;

    public SignedUrlService(StorageProperties properties) {
        String secret = properties.signedUrlSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.storage.signed-url-secret must be configured with at least 32 bytes.");
        }
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.defaultTtlSeconds = properties.signedUrlTtlSeconds() > 0 ? properties.signedUrlTtlSeconds() : 300;
    }

    public String createAssetContentUrl(UUID assetId, UUID organizationId) {
        long expiresAt = Instant.now().plusSeconds(defaultTtlSeconds).getEpochSecond();
        String sig = sign(assetId, organizationId, expiresAt);
        return "/api/v1/organizations/current/assets/" + assetId + "/content?expires=" + expiresAt + "&sig=" + sig;
    }

    public boolean isValidSignature(
            UUID assetId, UUID organizationId, long expiresEpochSeconds, String providedSignature) {
        if (providedSignature == null || providedSignature.isBlank()) {
            return false;
        }
        if (Instant.now().getEpochSecond() > expiresEpochSeconds) {
            return false;
        }
        String expected = sign(assetId, organizationId, expiresEpochSeconds);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), providedSignature.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(UUID assetId, UUID organizationId, long expiresEpochSeconds) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretBytes, HMAC_ALGORITHM));
            String payload = assetId + ":" + organizationId + ":" + expiresEpochSeconds;
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to sign asset URL", ex);
        }
    }
}

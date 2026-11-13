package com.smartcertificate.media;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartcertificate.common.config.StorageProperties;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SignedUrlServiceTest {

    private final SignedUrlService signedUrlService = new SignedUrlService(
            StorageProperties.ofLocal("./data/test", "signed-url-test-secret-minimum-32-bytes", 300, 1024));

    @Test
    void generatedSignedUrlValidatesForMatchingAssetAndOrganization() {
        UUID assetId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        String url = signedUrlService.createAssetContentUrl(assetId, orgId);
        Map<String, String> query = parseQuery(url);
        long expires = Long.parseLong(query.get("expires"));
        String sig = query.get("sig");

        assertTrue(signedUrlService.isValidSignature(assetId, orgId, expires, sig));
        assertFalse(signedUrlService.isValidSignature(assetId, UUID.randomUUID(), expires, sig));
        assertFalse(signedUrlService.isValidSignature(UUID.randomUUID(), orgId, expires, sig));
        assertFalse(signedUrlService.isValidSignature(assetId, orgId, expires + 1, sig));
    }

    @Test
    void rejectsExpiredSignature() {
        UUID assetId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String url = signedUrlService.createAssetContentUrl(assetId, orgId);
        Map<String, String> query = parseQuery(url);

        assertFalse(signedUrlService.isValidSignature(
                assetId, orgId, Instant.now().minusSeconds(10).getEpochSecond(), query.get("sig")));
    }

    private static Map<String, String> parseQuery(String url) {
        String query = URI.create(url).getQuery();
        return Arrays.stream(query.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1]));
    }
}

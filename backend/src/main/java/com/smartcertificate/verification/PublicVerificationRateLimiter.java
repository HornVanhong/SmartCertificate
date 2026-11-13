package com.smartcertificate.verification;

import com.smartcertificate.common.config.VerificationProperties;
import com.smartcertificate.common.exception.AppException;
import com.smartcertificate.common.exception.ErrorCode;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class PublicVerificationRateLimiter {

    private static final long WINDOW_SECONDS = 60L;
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final VerificationProperties verificationProperties;
    private final ConcurrentHashMap<String, WindowBucket> buckets = new ConcurrentHashMap<>();

    PublicVerificationRateLimiter(VerificationProperties verificationProperties) {
        this.verificationProperties = verificationProperties;
    }

    void checkLimit(String clientKey) {
        String key = (clientKey == null || clientKey.isBlank()) ? "unknown" : clientKey;
        long currentEpochMinute = Instant.now().getEpochSecond() / WINDOW_SECONDS;

        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            pruneStaleBuckets(currentEpochMinute);
        }

        WindowBucket bucket = buckets.compute(key, (k, existing) -> {
            if (existing == null || existing.epochMinute() != currentEpochMinute) {
                return new WindowBucket(currentEpochMinute, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });

        int maxRequests = verificationProperties.rateLimitPerMinute();
        if (bucket.count().get() > maxRequests) {
            throw new AppException(
                    ErrorCode.RATE_LIMITED,
                    "Too many certificate verification requests. Please wait a moment and try again.");
        }
    }

    private void pruneStaleBuckets(long currentEpochMinute) {
        for (Map.Entry<String, WindowBucket> entry : buckets.entrySet()) {
            if (entry.getValue().epochMinute() < currentEpochMinute - 1) {
                buckets.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private record WindowBucket(long epochMinute, AtomicInteger count) {}
}

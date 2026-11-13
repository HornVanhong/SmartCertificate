package com.smartcertificate.common.health;

import java.time.Instant;

public record HealthStatusResponse(String status, String service, String database, Instant timestamp) {}

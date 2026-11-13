package com.smartcertificate.batch;

import java.util.Map;
import java.util.UUID;

public record BatchErrorItemResponse(
        UUID id, int rowNumber, BatchItemStatus status, String errorMessage, Map<String, String> inputData) {}

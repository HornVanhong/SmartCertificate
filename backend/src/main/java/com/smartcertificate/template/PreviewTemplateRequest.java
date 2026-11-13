package com.smartcertificate.template;

import java.util.Map;

public record PreviewTemplateRequest(DesignDocument designJson, Map<String, String> sampleData) {}

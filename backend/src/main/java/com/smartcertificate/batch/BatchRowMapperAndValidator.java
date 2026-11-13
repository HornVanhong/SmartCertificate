package com.smartcertificate.batch;

import com.smartcertificate.certificate.IssueCertificateRequest;
import com.smartcertificate.template.DesignDocument;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class BatchRowMapperAndValidator {

    private static final Pattern PLACEHOLDER_TOKEN = Pattern.compile("\\{\\{([a-zA-Z0-9_]+)\\}\\}");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Set<String> AUTO_GENERATED_PLACEHOLDERS =
            Set.of("certificateNumber", "verificationUrl", "issuedDate", "organizationName");

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH));

    record RowOutcome(boolean valid, String errorMessage, IssueCertificateRequest issueRequest) {
        static RowOutcome valid(IssueCertificateRequest request) {
            return new RowOutcome(true, null, request);
        }

        static RowOutcome invalid(String message) {
            return new RowOutcome(false, message, null);
        }
    }

    List<String> extractTemplatePlaceholders(DesignDocument designJson) {
        Set<String> fields = new LinkedHashSet<>();
        fields.add("recipientName");
        fields.add("courseName");
        fields.add("recipientEmail");
        fields.add("completionDate");
        fields.add("expiresAt");

        if (designJson != null && designJson.elements() != null) {
            for (DesignDocument.DesignElement element : designJson.elements()) {
                if (element.text() == null) continue;
                Matcher matcher = PLACEHOLDER_TOKEN.matcher(element.text());
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (!AUTO_GENERATED_PLACEHOLDERS.contains(key)) {
                        fields.add(key);
                    }
                }
            }
        }
        return List.copyOf(fields);
    }

    Map<String, String> suggestInitialMapping(List<String> sourceHeaders, List<String> templatePlaceholders) {
        Map<String, String> normalizedHeaderLookup = new LinkedHashMap<>();
        for (String header : sourceHeaders) {
            normalizedHeaderLookup.put(normalizeKey(header), header);
        }

        Map<String, String> mapping = new LinkedHashMap<>();
        matchAlias(
                mapping,
                "recipientName",
                normalizedHeaderLookup,
                List.of("recipientname", "fullname", "name", "studentname", "learnername"));
        matchAlias(
                mapping,
                "courseName",
                normalizedHeaderLookup,
                List.of("coursename", "course", "programname", "program", "title"));
        matchAlias(
                mapping, "recipientEmail", normalizedHeaderLookup, List.of("recipientemail", "email", "emailaddress"));
        matchAlias(
                mapping,
                "completionDate",
                normalizedHeaderLookup,
                List.of("completiondate", "completedat", "date", "completeddate"));
        matchAlias(mapping, "expiresAt", normalizedHeaderLookup, List.of("expiresat", "expirationdate", "expirydate"));

        for (String field : templatePlaceholders) {
            if (mapping.containsKey(field)) continue;
            String matched = normalizedHeaderLookup.get(normalizeKey(field));
            if (matched != null) {
                mapping.put(field, matched);
            }
        }
        return mapping;
    }

    RowOutcome validateAndBuildIssueRequest(
            UUID templateId,
            Map<String, String> columnMapping,
            Map<String, String> inputData,
            String rowIdempotencyKey) {
        List<String> errors = new ArrayList<>();

        String recipientName = readMapped(columnMapping, inputData, "recipientName");
        if (recipientName.isEmpty()) {
            errors.add("recipientName is required.");
        } else if (recipientName.length() > 200) {
            errors.add("recipientName exceeds 200 characters.");
        }

        String courseName = readMapped(columnMapping, inputData, "courseName");
        if (courseName.isEmpty()) {
            errors.add("courseName is required.");
        } else if (courseName.length() > 200) {
            errors.add("courseName exceeds 200 characters.");
        }

        String recipientEmail = readMapped(columnMapping, inputData, "recipientEmail");
        if (!recipientEmail.isEmpty()) {
            if (recipientEmail.length() > 254
                    || !EMAIL_PATTERN.matcher(recipientEmail).matches()) {
                errors.add("recipientEmail is not a valid email address.");
            }
        } else {
            recipientEmail = null;
        }

        LocalDate completionDate = null;
        String completionRaw = readMapped(columnMapping, inputData, "completionDate");
        if (!completionRaw.isEmpty()) {
            completionDate = tryParseDate(completionRaw);
            if (completionDate == null) {
                errors.add("completionDate '" + completionRaw + "' must use YYYY-MM-DD or MM/DD/YYYY format.");
            }
        }

        Instant expiresAt = null;
        String expiresRaw = readMapped(columnMapping, inputData, "expiresAt");
        if (!expiresRaw.isEmpty()) {
            expiresAt = tryParseExpiration(expiresRaw);
            if (expiresAt == null) {
                errors.add("expiresAt '" + expiresRaw + "' must be a valid future date or ISO-8601 timestamp.");
            } else if (expiresAt.isBefore(Instant.now())) {
                errors.add("expiresAt must be in the future.");
            }
        }

        if (!errors.isEmpty()) {
            return RowOutcome.invalid(String.join(" ", errors));
        }

        Map<String, String> customMetadata = new LinkedHashMap<>();
        if (columnMapping != null) {
            for (Map.Entry<String, String> entry : columnMapping.entrySet()) {
                String targetField = entry.getKey();
                if (isStandardField(targetField)) continue;
                String val = readMapped(columnMapping, inputData, targetField);
                if (!val.isEmpty()) {
                    customMetadata.put(targetField, val);
                }
            }
        }

        IssueCertificateRequest request = new IssueCertificateRequest(
                templateId,
                recipientName,
                recipientEmail,
                courseName,
                completionDate,
                expiresAt,
                customMetadata.isEmpty() ? Map.of() : customMetadata,
                rowIdempotencyKey);
        return RowOutcome.valid(request);
    }

    private static boolean isStandardField(String field) {
        return "recipientName".equals(field)
                || "courseName".equals(field)
                || "recipientEmail".equals(field)
                || "completionDate".equals(field)
                || "expiresAt".equals(field);
    }

    private static String readMapped(
            Map<String, String> columnMapping, Map<String, String> inputData, String targetField) {
        if (columnMapping == null || inputData == null) return "";
        String header = columnMapping.get(targetField);
        if (header == null || header.isBlank()) return "";
        String value = inputData.get(header);
        return value == null ? "" : value.trim();
    }

    private static LocalDate tryParseDate(String raw) {
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(raw, formatter);
            } catch (DateTimeParseException ignored) {
                // try next pattern
            }
        }
        return null;
    }

    private static Instant tryParseExpiration(String raw) {
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
            LocalDate date = tryParseDate(raw);
            return date != null ? date.atTime(23, 59, 59).toInstant(ZoneOffset.UTC) : null;
        }
    }

    private static void matchAlias(
            Map<String, String> mapping, String target, Map<String, String> lookup, List<String> aliases) {
        for (String alias : aliases) {
            String header = lookup.get(alias);
            if (header != null) {
                mapping.put(target, header);
                return;
            }
        }
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
    }
}

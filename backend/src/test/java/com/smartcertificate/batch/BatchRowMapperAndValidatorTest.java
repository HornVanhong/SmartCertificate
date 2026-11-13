package com.smartcertificate.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchRowMapperAndValidatorTest {

    private final BatchRowMapperAndValidator validator = new BatchRowMapperAndValidator();

    @Test
    void suggestsColumnMappingsUsingHeaderAliases() {
        Map<String, String> suggested = validator.suggestInitialMapping(
                List.of("Full Name", "Email Address", "Program", "Completed Date", "instructorName"),
                List.of("recipientName", "courseName", "recipientEmail", "completionDate", "instructorName"));

        assertThat(suggested)
                .containsEntry("recipientName", "Full Name")
                .containsEntry("recipientEmail", "Email Address")
                .containsEntry("courseName", "Program")
                .containsEntry("completionDate", "Completed Date")
                .containsEntry("instructorName", "instructorName");
    }

    @Test
    void validatesFieldsDatesEmailsAndCustomMetadata() {
        UUID templateId = UUID.randomUUID();
        Map<String, String> mapping = Map.of(
                "recipientName", "name",
                "courseName", "course",
                "recipientEmail", "email",
                "completionDate", "date",
                "cohort", "cohort_col");

        var validOutcome = validator.validateAndBuildIssueRequest(
                templateId,
                mapping,
                Map.of(
                        "name", "Dara Sok",
                        "course", "Applied AI",
                        "email", "dara@example.org",
                        "date", "2026-11-05",
                        "cohort_col", "Cohort-2026"),
                "batch:1:row:1");

        assertThat(validOutcome.valid()).isTrue();
        assertThat(validOutcome.issueRequest().completionDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(validOutcome.issueRequest().metadata()).containsEntry("cohort", "Cohort-2026");

        var invalidOutcome = validator.validateAndBuildIssueRequest(
                templateId,
                mapping,
                Map.of(
                        "name", " ",
                        "course", "Applied AI",
                        "email", "not-an-email",
                        "date", "invalid-date"),
                "batch:1:row:2");

        assertThat(invalidOutcome.valid()).isFalse();
        assertThat(invalidOutcome.errorMessage())
                .contains("recipientName is required")
                .contains("recipientEmail is not a valid email address")
                .contains("completionDate");
    }
}

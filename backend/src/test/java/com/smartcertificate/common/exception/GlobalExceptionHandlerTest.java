package com.smartcertificate.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartcertificate.common.filter.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(
        controllers = GlobalExceptionHandlerTest.TestController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@Import({GlobalExceptionHandler.class, RequestIdFilter.class, GlobalExceptionHandlerTest.TestController.class})
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    @RequestMapping("/api/v1/test-errors")
    static class TestController {

        record SampleRequest(@NotBlank(message = "Recipient name is required.") String recipientName) {}

        @PostMapping("/validate")
        void validate(@Valid @RequestBody SampleRequest request) {}

        @GetMapping("/not-found")
        void notFound() {
            throw new AppException(ErrorCode.NOT_FOUND, "Template was not found.");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("Sensitive internal details");
        }
    }

    @Test
    void returnsFieldErrorsOnValidationFailure() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.fieldErrors.recipientName").value("Recipient name is required."))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @Test
    void returnsAppExceptionEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Template was not found."))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }

    @Test
    void hidesInternalExceptionMessage() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("An unexpected internal error occurred."))
                .andExpect(jsonPath("$.error.requestId").isNotEmpty());
    }
}

package com.smartcertificate.common.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartcertificate.common.filter.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = HealthController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@Import(RequestIdFilter.class)
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsStandardizedHealthEnvelopeAndRequestIdHeader() throws Exception {
        mockMvc.perform(get("/api/v1/health").header(RequestIdFilter.REQUEST_ID_HEADER, "req-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, "req-123"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.service").value("smart-certificate-backend"))
                .andExpect(jsonPath("$.data.database").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.meta.requestId").value("req-123"));
    }

    @Test
    void replacesUnsafeRequestIdWithGeneratedUuid() throws Exception {
        mockMvc.perform(get("/api/v1/health").header(RequestIdFilter.REQUEST_ID_HEADER, "bad\nlog"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.REQUEST_ID_HEADER))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }
}

package com.smartcertificate.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smartcertificate.common.config.SecurityProperties;
import com.smartcertificate.common.exception.GlobalExceptionHandler;
import com.smartcertificate.common.filter.RequestIdFilter;
import com.smartcertificate.organization.OrganizationController;
import com.smartcertificate.organization.OrganizationResponse;
import com.smartcertificate.organization.OrganizationService;
import com.smartcertificate.user.UserAccountService;
import com.smartcertificate.user.UserController;
import com.smartcertificate.user.UserManagementService;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {OrganizationController.class, UserController.class})
@EnableConfigurationProperties(SecurityProperties.class)
@Import({SecurityConfig.class, JwtService.class, GlobalExceptionHandler.class, RequestIdFilter.class})
@ActiveProfiles("test")
class RbacSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserAccountService userAccountService;

    @MockitoBean
    private OrganizationService organizationService;

    @MockitoBean
    private UserManagementService userManagementService;

    @Test
    void rejectsUnauthenticatedRequestsWithStandardized401() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void forbidsGeneralUserFromUpdatingOrganizationProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        when(userAccountService.loadActiveMembership(userId, orgId))
                .thenReturn(Optional.of(new UserAccountService.MembershipAuthRecord(
                        userId,
                        orgId,
                        "member@org.test",
                        "Member User",
                        "GENERAL_USER",
                        Set.of("org:read", "template:read", "certificate:read"))));

        String token = jwtService.generateAccessToken(userId, orgId, "member@org.test");
        mockMvc.perform(put("/api/v1/organizations/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Org\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void allowsGeneralUserWithOrgReadToViewCurrentOrganization() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        when(userAccountService.loadActiveMembership(userId, orgId))
                .thenReturn(Optional.of(new UserAccountService.MembershipAuthRecord(
                        userId, orgId, "member@org.test", "Member User", "GENERAL_USER", Set.of("org:read"))));
        when(organizationService.getCurrentOrganization(any()))
                .thenReturn(new OrganizationResponse(
                        orgId,
                        "Org One",
                        "org-one",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of(),
                        Instant.now(),
                        Instant.now()));

        String token = jwtService.generateAccessToken(userId, orgId, "member@org.test");
        mockMvc.perform(get("/api/v1/organizations/current").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Org One"));
    }
}

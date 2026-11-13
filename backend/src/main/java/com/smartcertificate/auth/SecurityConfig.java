package com.smartcertificate.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcertificate.common.api.ApiErrorResponse;
import com.smartcertificate.common.exception.ErrorCode;
import com.smartcertificate.common.filter.RequestIdFilter;
import com.smartcertificate.user.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtService jwtService, UserAccountService userAccountService, ObjectMapper objectMapper)
            throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService, userAccountService);

        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokenRepository.setCookiePath("/");

        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        csrfRequestHandler.setCsrfRequestAttributeName(null);

        RequestMatcher csrfIgnoredMatcher = this::shouldIgnoreCsrf;

        http.cors(Customizer.withDefaults())
                .headers(headers -> headers.contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(ref ->
                                ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(policy -> policy.policy("camera=(), microphone=(), geolocation=()")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        .ignoringRequestMatchers(csrfIgnoredMatcher))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/api/v1/health",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/invitations/accept",
                                "/api/v1/auth/csrf",
                                "/api/v1/public/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/organizations/current/assets/*/content")
                        .permitAll()
                        .requestMatchers("/api/**")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) ->
                                writeError(response, objectMapper, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(response, objectMapper, ErrorCode.FORBIDDEN)))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private boolean shouldIgnoreCsrf(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path != null
                && (path.equals("/api/v1/auth/login")
                        || path.equals("/api/v1/auth/register")
                        || path.equals("/api/v1/auth/forgot-password")
                        || path.equals("/api/v1/auth/reset-password")
                        || path.equals("/api/v1/auth/invitations/accept")
                        || path.startsWith("/api/v1/public/"))) {
            return true;
        }
        boolean hasAuthCookie = hasAnyAuthCookie(request);
        if (!hasAuthCookie
                && path != null
                && (path.equals("/api/v1/auth/refresh") || path.equals("/api/v1/auth/logout"))) {
            return true;
        }
        // Pure Bearer token API requests (with no browser session cookies) do not need CSRF tokens.
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        return !hasAuthCookie && authHeader != null && authHeader.startsWith("Bearer ");
    }

    private boolean hasAnyAuthCookie(HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (var cookie : cookies) {
            if (AuthCookieService.ACCESS_COOKIE_NAME.equals(cookie.getName())
                    || AuthCookieService.REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }

    private void writeError(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode errorCode)
            throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body = ApiErrorResponse.of(
                errorCode.name(), errorCode.getDefaultMessage(), RequestIdFilter.currentRequestId());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

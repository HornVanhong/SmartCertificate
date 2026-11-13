package com.smartcertificate.auth;

public record AuthSessionResponse(
        String accessToken, String refreshToken, long expiresInSeconds, AuthMeResponse session) {}

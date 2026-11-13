package com.smartcertificate.auth;

import java.util.UUID;

public interface AuthService {

    AuthSessionResponse register(RegisterRequest request);

    AuthSessionResponse login(LoginRequest request);

    AuthSessionResponse refresh(String rawRefreshToken);

    void logout(String rawRefreshToken);

    void forgotPassword(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    AuthSessionResponse acceptInvitation(AcceptInvitationRequest request);

    AuthMeResponse getCurrentSession(UUID userId, UUID organizationId);
}

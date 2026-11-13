package com.smartcertificate.verification;

public interface PublicVerificationService {

    PublicVerificationResponse verifyByToken(String rawToken);
}

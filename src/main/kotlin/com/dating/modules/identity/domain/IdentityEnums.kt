package com.dating.modules.identity.domain

enum class UserStatus {
    PENDING_VERIFICATION,
    PENDING_AGREEMENTS,
    ACTIVE,
    SUSPENDED,
    DELETED,
    ANONYMIZED,
    BANNED,
}

enum class OAuthProvider {
    GOOGLE,
    NAVER,
    KAKAO,
    APPLE,
}

enum class AgreementType {
    TERMS_OF_SERVICE,
    PRIVACY_POLICY,
    LOCATION_BASED,
    MARKETING,
    NIGHT_MARKETING,
}

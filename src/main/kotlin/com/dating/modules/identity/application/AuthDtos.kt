package com.dating.modules.identity.application

import com.dating.modules.identity.domain.AgreementType
import com.dating.modules.identity.domain.OAuthProvider
import com.dating.modules.identity.domain.UserStatus
import com.dating.modules.profile.domain.GenderType
import com.dating.platform.security.TokenPair
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.time.LocalDate
import java.time.Instant

data class OAuthLoginRequest(
    @field:NotBlank
    val providerUserId: String,
    @field:NotBlank
    val deviceId: String,
    val providerEmail: String? = null,
    val email: String? = providerEmail,
)

data class AuthUserResult(
    val userId: Long,
    val provider: OAuthProvider? = null,
    val status: UserStatus,
)

data class AuthSessionResult(
    val user: AuthUserResult,
    val tokens: TokenPair,
)

data class RefreshTokenRequest(
    @field:NotBlank
    val refreshToken: String,
)

data class TokenRefreshResult(
    val tokens: TokenPair,
)

data class AuthSessionInfo(
    val id: Long,
    val deviceId: String,
    val expiresAt: Instant,
    val lastUsedAt: Instant,
    val revokedAt: Instant?,
)

data class IdentityVerificationRequest(
    @field:NotBlank
    val ciHash: String,
    @field:NotBlank
    val diHash: String,
    val verifiedBirthDate: LocalDate,
    val verifiedGender: GenderType,
    val carrier: String? = null,
)

data class AgreementInput(
    val agreementType: AgreementType,
    @field:NotBlank
    val version: String,
    val agreed: Boolean,
)

data class AgreementsRequest(
    @field:Valid
    @field:NotEmpty
    val agreements: List<AgreementInput>,
)

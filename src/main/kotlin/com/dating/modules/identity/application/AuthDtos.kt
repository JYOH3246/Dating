package com.dating.modules.identity.application

import com.dating.modules.identity.domain.AgreementType
import com.dating.modules.identity.domain.OAuthProvider
import com.dating.modules.identity.domain.UserStatus
import com.dating.modules.profile.domain.GenderType
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.time.LocalDate

data class OAuthLoginRequest(
    @field:NotBlank
    val providerUserId: String,
    val providerEmail: String? = null,
    val email: String? = providerEmail,
)

data class AuthUserResult(
    val userId: Long,
    val provider: OAuthProvider? = null,
    val status: UserStatus,
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

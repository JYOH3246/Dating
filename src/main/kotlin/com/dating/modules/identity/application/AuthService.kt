package com.dating.modules.identity.application

import com.dating.modules.identity.domain.AgreementType
import com.dating.modules.identity.domain.OAuthProvider
import com.dating.modules.identity.domain.UserStatus
import com.dating.modules.identity.infrastructure.jpa.AuthIdentityJpaEntity
import com.dating.modules.identity.infrastructure.jpa.AuthIdentityJpaRepository
import com.dating.modules.identity.infrastructure.jpa.BannedIdentityJpaRepository
import com.dating.modules.identity.infrastructure.jpa.UserAgreementJpaEntity
import com.dating.modules.identity.infrastructure.jpa.UserAgreementJpaRepository
import com.dating.modules.identity.infrastructure.jpa.UserIdentityVerificationJpaEntity
import com.dating.modules.identity.infrastructure.jpa.UserIdentityVerificationJpaRepository
import com.dating.modules.identity.infrastructure.jpa.UserJpaEntity
import com.dating.modules.identity.infrastructure.jpa.UserJpaRepository
import com.dating.modules.notification.infrastructure.jpa.NotificationSettingJpaEntity
import com.dating.modules.notification.infrastructure.jpa.NotificationSettingJpaRepository
import com.dating.modules.profile.infrastructure.jpa.MatchPreferenceJpaEntity
import com.dating.modules.profile.infrastructure.jpa.MatchPreferenceJpaRepository
import com.dating.shared.exception.DatingException
import com.dating.shared.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.Period

@Service
class AuthService(
    private val userRepository: UserJpaRepository,
    private val authIdentityRepository: AuthIdentityJpaRepository,
    private val verificationRepository: UserIdentityVerificationJpaRepository,
    private val bannedIdentityRepository: BannedIdentityJpaRepository,
    private val agreementRepository: UserAgreementJpaRepository,
    private val notificationSettingRepository: NotificationSettingJpaRepository,
    private val matchPreferenceRepository: MatchPreferenceJpaRepository,
    private val userAccessService: UserAccessService,
) {
    @Transactional
    fun oauthLogin(providerName: String, request: OAuthLoginRequest): AuthUserResult {
        val provider = parseProvider(providerName)
        val existingIdentity = authIdentityRepository
            .findByProviderAndProviderUserIdAndDeletedAtIsNull(provider, request.providerUserId)

        if (existingIdentity != null) {
            val user = userAccessService.getUser(existingIdentity.userId)
            return AuthUserResult(
                userId = user.id ?: error("Persisted user is missing id."),
                provider = provider,
                status = user.status,
            )
        }

        val user = userRepository.save(
            UserJpaEntity(
                email = request.email ?: request.providerEmail,
                status = UserStatus.PENDING_VERIFICATION,
            ),
        )
        val userId = user.id ?: error("Persisted user is missing id.")

        authIdentityRepository.save(
            AuthIdentityJpaEntity(
                userId = userId,
                provider = provider,
                providerUserId = request.providerUserId,
                providerEmail = request.providerEmail,
            ),
        )
        notificationSettingRepository.save(NotificationSettingJpaEntity(userId = userId))

        return AuthUserResult(userId = userId, provider = provider, status = user.status)
    }

    @Transactional
    fun verifyIdentity(userId: Long, request: IdentityVerificationRequest): AuthUserResult {
        val user = userAccessService.getUser(userId)
        if (user.status != UserStatus.PENDING_VERIFICATION) {
            throw DatingException(ErrorCode.CONFLICT, "Identity verification is only allowed before agreements.")
        }
        if (age(request.verifiedBirthDate) < 19) {
            throw DatingException(ErrorCode.PERMISSION_DENIED, "Only users aged 19 or older may join.")
        }
        if (bannedIdentityRepository.existsByDiHash(request.diHash)) {
            throw DatingException(ErrorCode.PERMISSION_DENIED, "This identity is banned.")
        }
        if (verificationRepository.existsByDiHashAndIsActiveTrue(request.diHash)) {
            throw DatingException(ErrorCode.CONFLICT, "This identity is already active.")
        }

        verificationRepository.save(
            UserIdentityVerificationJpaEntity(
                userId = userId,
                ciHash = request.ciHash,
                diHash = request.diHash,
                verifiedBirthDate = request.verifiedBirthDate,
                verifiedGender = request.verifiedGender,
                carrier = request.carrier,
            ),
        )
        user.status = UserStatus.PENDING_AGREEMENTS

        return AuthUserResult(userId = userId, status = user.status)
    }

    @Transactional
    fun acceptAgreements(userId: Long, request: AgreementsRequest): AuthUserResult {
        val user = userAccessService.getUser(userId)
        if (user.status != UserStatus.PENDING_AGREEMENTS) {
            throw DatingException(ErrorCode.CONFLICT, "Agreements are only allowed after identity verification.")
        }

        val latestAgreements = request.agreements.associateBy { it.agreementType }
        val missingRequired = REQUIRED_AGREEMENTS.filter { latestAgreements[it]?.agreed != true }
        if (missingRequired.isNotEmpty()) {
            throw DatingException(ErrorCode.INVALID_ARGUMENT, "Required agreements are missing: $missingRequired")
        }

        request.agreements.forEach { agreement ->
            agreementRepository.save(
                UserAgreementJpaEntity(
                    userId = userId,
                    agreementType = agreement.agreementType,
                    version = agreement.version,
                    isAgreed = agreement.agreed,
                ),
            )
        }

        user.status = UserStatus.ACTIVE
        if (matchPreferenceRepository.findByUserId(userId) == null) {
            matchPreferenceRepository.save(MatchPreferenceJpaEntity(userId = userId))
        }

        return AuthUserResult(userId = userId, status = user.status)
    }

    private fun parseProvider(providerName: String): OAuthProvider {
        return runCatching { OAuthProvider.valueOf(providerName.uppercase()) }
            .getOrElse { throw DatingException(ErrorCode.INVALID_ARGUMENT, "Unsupported OAuth provider.") }
    }

    private fun age(birthDate: LocalDate): Int {
        return Period.between(birthDate, LocalDate.now()).years
    }

    private companion object {
        val REQUIRED_AGREEMENTS = setOf(
            AgreementType.TERMS_OF_SERVICE,
            AgreementType.PRIVACY_POLICY,
            AgreementType.LOCATION_BASED,
        )
    }
}

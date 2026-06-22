package com.dating.modules.identity.application

import com.dating.modules.identity.domain.AgreementType
import com.dating.modules.identity.domain.OAuthProvider
import com.dating.modules.identity.domain.UserStatus
import com.dating.modules.identity.infrastructure.jpa.AuthIdentityJpaEntity
import com.dating.modules.identity.infrastructure.jpa.AuthIdentityJpaRepository
import com.dating.modules.identity.infrastructure.jpa.AuthTokenSessionJpaEntity
import com.dating.modules.identity.infrastructure.jpa.AuthTokenSessionJpaRepository
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
import com.dating.platform.security.PasetoTokenService
import com.dating.platform.security.RefreshTokenHasher
import com.dating.platform.security.TokenPair
import com.dating.platform.security.TokenType
import com.dating.platform.security.AuthTokenProperties
import com.dating.shared.exception.DatingException
import com.dating.shared.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserJpaRepository,
    private val authIdentityRepository: AuthIdentityJpaRepository,
    private val verificationRepository: UserIdentityVerificationJpaRepository,
    private val tokenSessionRepository: AuthTokenSessionJpaRepository,
    private val bannedIdentityRepository: BannedIdentityJpaRepository,
    private val agreementRepository: UserAgreementJpaRepository,
    private val notificationSettingRepository: NotificationSettingJpaRepository,
    private val matchPreferenceRepository: MatchPreferenceJpaRepository,
    private val userAccessService: UserAccessService,
    private val pasetoTokenService: PasetoTokenService,
    private val refreshTokenHasher: RefreshTokenHasher,
    private val authTokenProperties: AuthTokenProperties,
) {
    @Transactional
    fun oauthLogin(providerName: String, request: OAuthLoginRequest): AuthSessionResult {
        val provider = parseProvider(providerName)
        val existingIdentity = authIdentityRepository
            .findByProviderAndProviderUserIdAndDeletedAtIsNull(provider, request.providerUserId)

        val user = if (existingIdentity != null) {
            val user = userAccessService.getUser(existingIdentity.userId)
            if (user.status == UserStatus.BANNED || user.status == UserStatus.ANONYMIZED) {
                throw DatingException(ErrorCode.PERMISSION_DENIED, "User cannot log in.")
            }
            user
        } else userRepository.save(
            UserJpaEntity(
                email = request.email ?: request.providerEmail,
                status = UserStatus.PENDING_VERIFICATION,
            ),
        )
        val userId = user.id ?: error("Persisted user is missing id.")

        if (existingIdentity == null) {
            authIdentityRepository.save(
                AuthIdentityJpaEntity(
                    userId = userId,
                    provider = provider,
                    providerUserId = request.providerUserId,
                    providerEmail = request.providerEmail,
                ),
            )
            notificationSettingRepository.save(NotificationSettingJpaEntity(userId = userId))
        }

        val tokens = issueSessionTokens(userId = userId, deviceId = request.deviceId)
        return AuthSessionResult(
            user = AuthUserResult(userId = userId, provider = provider, status = user.status),
            tokens = tokens,
        )
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

    @Transactional(noRollbackFor = [DatingException::class])
    fun refresh(request: RefreshTokenRequest): TokenRefreshResult {
        val claims = pasetoTokenService.parse(request.refreshToken, TokenType.REFRESH)
        val userId = claims.sub.toLongOrNull()
            ?: throw DatingException(ErrorCode.UNAUTHENTICATED, "Invalid token subject.")
        val now = Instant.now()
        val session = tokenSessionRepository.findById(claims.sid).orElse(null)

        if (
            session == null ||
            session.userId != userId ||
            session.revokedAt != null ||
            session.refreshTokenHash != refreshTokenHasher.hash(request.refreshToken)
        ) {
            revokeActiveSessions(userId, now)
            throw DatingException(ErrorCode.UNAUTHENTICATED, "Refresh token reuse was detected.")
        }

        if (!session.expiresAt.isAfter(now)) {
            session.revokedAt = now
            throw DatingException(ErrorCode.UNAUTHENTICATED, "Refresh token has expired.")
        }

        val (tokenPair, refreshToken) = pasetoTokenService.issueTokenPair(userId, session.idOrThrow())
        session.refreshTokenHash = refreshTokenHasher.hash(refreshToken.token)
        session.expiresAt = refreshToken.claims.exp
        session.rotatedAt = now
        session.lastUsedAt = now

        return TokenRefreshResult(tokens = tokenPair)
    }

    @Transactional
    fun logout(userId: Long, sessionId: Long) {
        val session = tokenSessionRepository.findByIdAndRevokedAtIsNull(sessionId)
            ?: throw DatingException(ErrorCode.NOT_FOUND, "Session was not found.")
        if (session.userId != userId) {
            throw DatingException(ErrorCode.PERMISSION_DENIED, "You cannot revoke this session.")
        }
        session.revokedAt = Instant.now()
    }

    @Transactional(readOnly = true)
    fun getSessions(userId: Long): List<AuthSessionInfo> {
        userAccessService.requireActive(userId)
        return tokenSessionRepository.findActiveByUserId(userId)
            .map { it.toInfo() }
    }

    @Transactional
    fun revokeSession(userId: Long, sessionId: Long) {
        userAccessService.requireActive(userId)
        val session = tokenSessionRepository.findByIdAndRevokedAtIsNull(sessionId)
            ?: throw DatingException(ErrorCode.NOT_FOUND, "Session was not found.")
        if (session.userId != userId) {
            throw DatingException(ErrorCode.PERMISSION_DENIED, "You cannot revoke this session.")
        }
        session.revokedAt = Instant.now()
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

    private fun issueSessionTokens(userId: Long, deviceId: String): TokenPair {
        val now = Instant.now()
        val session = tokenSessionRepository.findByUserIdAndDeviceIdAndRevokedAtIsNull(userId, deviceId)
            ?: tokenSessionRepository.save(
                AuthTokenSessionJpaEntity(
                    userId = userId,
                    deviceId = deviceId,
                    refreshTokenHash = "pending-${UUID.randomUUID()}",
                    expiresAt = now.plus(authTokenProperties.refreshTokenTtl),
                    lastUsedAt = now,
                ),
            )

        val sessionId = session.idOrThrow()
        val (tokenPair, refreshToken) = pasetoTokenService.issueTokenPair(userId, sessionId)
        session.refreshTokenHash = refreshTokenHasher.hash(refreshToken.token)
        session.expiresAt = refreshToken.claims.exp
        session.lastUsedAt = now
        session.revokedAt = null

        revokeOverflowSessions(userId, keepSessionId = sessionId, now = now)
        return tokenPair
    }

    private fun revokeOverflowSessions(userId: Long, keepSessionId: Long, now: Instant) {
        val activeSessions = tokenSessionRepository.findByUserIdAndRevokedAtIsNullOrderByLastUsedAtAsc(userId)
        val overflowCount = activeSessions.size - authTokenProperties.maxActiveSessions
        if (overflowCount <= 0) {
            return
        }

        activeSessions
            .filter { it.id != keepSessionId }
            .take(overflowCount)
            .forEach { it.revokedAt = now }
    }

    private fun revokeActiveSessions(userId: Long, now: Instant) {
        tokenSessionRepository.findActiveByUserId(userId)
            .forEach { it.revokedAt = now }
    }

    private fun AuthTokenSessionJpaEntity.toInfo(): AuthSessionInfo =
        AuthSessionInfo(
            id = idOrThrow(),
            deviceId = deviceId,
            expiresAt = expiresAt,
            lastUsedAt = lastUsedAt,
            revokedAt = revokedAt,
        )

    private fun AuthTokenSessionJpaEntity.idOrThrow(): Long = id ?: error("Persisted token session is missing id.")

    private companion object {
        val REQUIRED_AGREEMENTS = setOf(
            AgreementType.TERMS_OF_SERVICE,
            AgreementType.PRIVACY_POLICY,
            AgreementType.LOCATION_BASED,
        )
    }
}

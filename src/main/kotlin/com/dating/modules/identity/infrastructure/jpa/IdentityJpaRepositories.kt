package com.dating.modules.identity.infrastructure.jpa

import com.dating.modules.identity.domain.OAuthProvider
import com.dating.modules.identity.domain.PasetoKeyStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserJpaRepository : JpaRepository<UserJpaEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): UserJpaEntity?
}

interface AuthIdentityJpaRepository : JpaRepository<AuthIdentityJpaEntity, Long> {
    fun findByProviderAndProviderUserIdAndDeletedAtIsNull(
        provider: OAuthProvider,
        providerUserId: String,
    ): AuthIdentityJpaEntity?
}

interface UserIdentityVerificationJpaRepository : JpaRepository<UserIdentityVerificationJpaEntity, Long> {
    fun existsByDiHashAndIsActiveTrue(diHash: String): Boolean

    fun findFirstByUserIdAndIsActiveTrueOrderByVerifiedAtDesc(userId: Long): UserIdentityVerificationJpaEntity?
}

interface BannedIdentityJpaRepository : JpaRepository<BannedIdentityJpaEntity, Long> {
    fun existsByDiHash(diHash: String): Boolean
}

interface UserAgreementJpaRepository : JpaRepository<UserAgreementJpaEntity, Long>

interface AuthTokenSessionJpaRepository : JpaRepository<AuthTokenSessionJpaEntity, Long> {
    fun findByIdAndRevokedAtIsNull(id: Long): AuthTokenSessionJpaEntity?

    fun findByUserIdAndDeviceIdAndRevokedAtIsNull(userId: Long, deviceId: String): AuthTokenSessionJpaEntity?

    fun findByUserIdAndRevokedAtIsNullOrderByLastUsedAtAsc(userId: Long): List<AuthTokenSessionJpaEntity>

    @Query(
        """
        select s from AuthTokenSessionJpaEntity s
        where s.userId = :userId
          and s.revokedAt is null
        """,
    )
    fun findActiveByUserId(@Param("userId") userId: Long): List<AuthTokenSessionJpaEntity>
}

interface PasetoKeyJpaRepository : JpaRepository<PasetoKeyJpaEntity, String> {
    fun findFirstByStatus(status: PasetoKeyStatus): PasetoKeyJpaEntity?
}

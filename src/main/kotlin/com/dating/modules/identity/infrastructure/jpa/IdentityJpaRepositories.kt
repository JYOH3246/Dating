package com.dating.modules.identity.infrastructure.jpa

import com.dating.modules.identity.domain.OAuthProvider
import org.springframework.data.jpa.repository.JpaRepository

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

package com.dating.modules.identity.infrastructure.jpa

import com.dating.modules.identity.domain.AgreementType
import com.dating.modules.identity.domain.OAuthProvider
import com.dating.modules.identity.domain.UserStatus
import com.dating.modules.profile.domain.GenderType
import com.dating.platform.persistence.BaseJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "users")
class UserJpaEntity(
    @Column(name = "email")
    var email: String? = null,

    @Column(name = "phone_number")
    var phoneNumber: String? = null,

    @Column(name = "name")
    var name: String? = null,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "user_status")
    var status: UserStatus = UserStatus.PENDING_VERIFICATION,

    @Column(name = "anonymized_at")
    var anonymizedAt: Instant? = null,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
) : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

@Entity
@Table(name = "auth_identities")
class AuthIdentityJpaEntity(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "provider", nullable = false, columnDefinition = "oauth_provider")
    var provider: OAuthProvider,

    @Column(name = "provider_user_id", nullable = false)
    var providerUserId: String,

    @Column(name = "provider_email")
    var providerEmail: String? = null,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
) : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

@Entity
@Table(name = "user_identity_verifications")
class UserIdentityVerificationJpaEntity(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "ci_hash", nullable = false)
    var ciHash: String,

    @Column(name = "di_hash", nullable = false)
    var diHash: String,

    @Column(name = "verified_birth_date", nullable = false)
    var verifiedBirthDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "verified_gender", nullable = false, columnDefinition = "gender_type")
    var verifiedGender: GenderType,

    @Column(name = "carrier")
    var carrier: String? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "verified_at", nullable = false)
    var verifiedAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

@Entity
@Table(name = "banned_identities")
class BannedIdentityJpaEntity(
    @Column(name = "di_hash", nullable = false)
    var diHash: String,

    @Column(name = "reason")
    var reason: String? = null,

    @Column(name = "banned_at", nullable = false)
    var bannedAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

@Entity
@Table(name = "user_agreements")
class UserAgreementJpaEntity(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "agreement_type", nullable = false, columnDefinition = "agreement_type")
    var agreementType: AgreementType,

    @Column(name = "version", nullable = false)
    var version: String,

    @Column(name = "is_agreed", nullable = false)
    var isAgreed: Boolean,

    @Column(name = "agreed_at", nullable = false)
    var agreedAt: Instant = Instant.now(),

    @Column(name = "ip_address", columnDefinition = "inet")
    var ipAddress: String? = null,

    @Column(name = "user_agent")
    var userAgent: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

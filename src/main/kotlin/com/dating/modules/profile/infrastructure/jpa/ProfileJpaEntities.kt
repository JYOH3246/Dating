package com.dating.modules.profile.infrastructure.jpa

import com.dating.modules.profile.domain.GenderType
import com.dating.modules.profile.domain.PhotoStatus
import com.dating.modules.profile.domain.ProfileStatus
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
@Table(name = "profiles")
class ProfileJpaEntity(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "nickname")
    var nickname: String? = null,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "gender", nullable = false, columnDefinition = "gender_type")
    var gender: GenderType = GenderType.UNKNOWN,

    @Column(name = "birth_date")
    var birthDate: LocalDate? = null,

    @Column(name = "region_code")
    var regionCode: String? = null,

    @Column(name = "bio")
    var bio: String? = null,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "profile_status")
    var status: ProfileStatus = ProfileStatus.DRAFT,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
) : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

@Entity
@Table(name = "profile_photos")
class ProfilePhotoJpaEntity(
    @Column(name = "profile_id", nullable = false)
    var profileId: Long,

    @Column(name = "file_key", nullable = false)
    var fileKey: String,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Short,

    @Column(name = "is_primary", nullable = false)
    var isPrimary: Boolean = false,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "photo_status")
    var status: PhotoStatus = PhotoStatus.PENDING,

    @Column(name = "deleted_at")
    var deletedAt: Instant? = null,
) : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

@Entity
@Table(name = "interests")
class InterestJpaEntity(
    @Column(name = "code", nullable = false)
    var code: String,

    @Column(name = "label", nullable = false)
    var label: String,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

@Entity
@Table(name = "profile_interests")
class ProfileInterestJpaEntity(
    @Column(name = "profile_id", nullable = false)
    var profileId: Long,

    @Column(name = "interest_id", nullable = false)
    var interestId: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

@Entity
@Table(name = "match_preferences")
class MatchPreferenceJpaEntity(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "age_min", nullable = false)
    var ageMin: Short = 19,

    @Column(name = "age_max", nullable = false)
    var ageMax: Short = 99,
) : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

@Entity
@Table(name = "match_preference_regions")
class MatchPreferenceRegionJpaEntity(
    @Column(name = "match_preference_id", nullable = false)
    var matchPreferenceId: Long,

    @Column(name = "region_code", nullable = false)
    var regionCode: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

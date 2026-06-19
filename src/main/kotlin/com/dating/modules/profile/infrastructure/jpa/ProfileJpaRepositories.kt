package com.dating.modules.profile.infrastructure.jpa

import com.dating.modules.profile.domain.PhotoStatus
import org.springframework.data.jpa.repository.JpaRepository

interface ProfileJpaRepository : JpaRepository<ProfileJpaEntity, Long> {
    fun findByUserIdAndDeletedAtIsNull(userId: Long): ProfileJpaEntity?
}

interface ProfilePhotoJpaRepository : JpaRepository<ProfilePhotoJpaEntity, Long> {
    fun findByProfileIdAndDeletedAtIsNullOrderByDisplayOrderAsc(profileId: Long): List<ProfilePhotoJpaEntity>

    fun countByProfileIdAndDeletedAtIsNull(profileId: Long): Long

    fun existsByProfileIdAndStatusAndDeletedAtIsNull(profileId: Long, status: PhotoStatus): Boolean

    fun findByIdAndProfileIdAndDeletedAtIsNull(id: Long, profileId: Long): ProfilePhotoJpaEntity?
}

interface InterestJpaRepository : JpaRepository<InterestJpaEntity, Long> {
    fun findByCode(code: String): InterestJpaEntity?

    fun findByIdIn(ids: Collection<Long>): List<InterestJpaEntity>
}

interface ProfileInterestJpaRepository : JpaRepository<ProfileInterestJpaEntity, Long> {
    fun findByProfileId(profileId: Long): List<ProfileInterestJpaEntity>

    fun deleteByProfileId(profileId: Long)
}

interface MatchPreferenceJpaRepository : JpaRepository<MatchPreferenceJpaEntity, Long> {
    fun findByUserId(userId: Long): MatchPreferenceJpaEntity?
}

interface MatchPreferenceRegionJpaRepository : JpaRepository<MatchPreferenceRegionJpaEntity, Long> {
    fun deleteByMatchPreferenceId(matchPreferenceId: Long)
}

package com.dating.modules.profile.application

import com.dating.modules.identity.application.UserAccessService
import com.dating.modules.identity.infrastructure.jpa.UserIdentityVerificationJpaRepository
import com.dating.modules.profile.domain.PhotoStatus
import com.dating.modules.profile.domain.ProfileStatus
import com.dating.modules.profile.infrastructure.jpa.InterestJpaEntity
import com.dating.modules.profile.infrastructure.jpa.InterestJpaRepository
import com.dating.modules.profile.infrastructure.jpa.MatchPreferenceJpaEntity
import com.dating.modules.profile.infrastructure.jpa.MatchPreferenceJpaRepository
import com.dating.modules.profile.infrastructure.jpa.MatchPreferenceRegionJpaEntity
import com.dating.modules.profile.infrastructure.jpa.MatchPreferenceRegionJpaRepository
import com.dating.modules.profile.infrastructure.jpa.ProfileInterestJpaEntity
import com.dating.modules.profile.infrastructure.jpa.ProfileInterestJpaRepository
import com.dating.modules.profile.infrastructure.jpa.ProfileJpaEntity
import com.dating.modules.profile.infrastructure.jpa.ProfileJpaRepository
import com.dating.modules.profile.infrastructure.jpa.ProfilePhotoJpaEntity
import com.dating.modules.profile.infrastructure.jpa.ProfilePhotoJpaRepository
import com.dating.shared.exception.DatingException
import com.dating.shared.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ProfileService(
    private val userAccessService: UserAccessService,
    private val verificationRepository: UserIdentityVerificationJpaRepository,
    private val profileRepository: ProfileJpaRepository,
    private val profilePhotoRepository: ProfilePhotoJpaRepository,
    private val interestRepository: InterestJpaRepository,
    private val profileInterestRepository: ProfileInterestJpaRepository,
    private val matchPreferenceRepository: MatchPreferenceJpaRepository,
    private val matchPreferenceRegionRepository: MatchPreferenceRegionJpaRepository,
) {
    @Transactional
    fun createProfile(userId: Long, request: UpsertProfileRequest): ProfileResult {
        userAccessService.requireWritable(userId)
        profileRepository.findByUserIdAndDeletedAtIsNull(userId)?.let {
            applyProfileFields(it, request)
            saveInterests(it.idOrThrow(), request.interestCodes)
            saveMatchPreferences(userId, request)
            return toResult(it)
        }

        val verification = verificationRepository.findFirstByUserIdAndIsActiveTrueOrderByVerifiedAtDesc(userId)
        val profile = profileRepository.save(
            ProfileJpaEntity(
                userId = userId,
                nickname = request.nickname,
                gender = verification?.verifiedGender ?: com.dating.modules.profile.domain.GenderType.UNKNOWN,
                birthDate = verification?.verifiedBirthDate,
                regionCode = request.regionCode,
                bio = request.bio,
                status = ProfileStatus.DRAFT,
            ),
        )

        saveInterests(profile.idOrThrow(), request.interestCodes)
        saveMatchPreferences(userId, request)
        return toResult(profile)
    }

    @Transactional
    fun updateMyProfile(userId: Long, request: UpsertProfileRequest): ProfileResult {
        userAccessService.requireWritable(userId)
        val profile = getMyProfileEntity(userId)
        applyProfileFields(profile, request)
        if (profile.status == ProfileStatus.REJECTED) {
            profile.status = ProfileStatus.DRAFT
        }
        saveInterests(profile.idOrThrow(), request.interestCodes)
        saveMatchPreferences(userId, request)
        return toResult(profile)
    }

    @Transactional(readOnly = true)
    fun getMyProfile(userId: Long): ProfileResult {
        userAccessService.requireActive(userId)
        return toResult(getMyProfileEntity(userId))
    }

    @Transactional
    fun submitMyProfile(userId: Long): ProfileResult {
        userAccessService.requireWritable(userId)
        val profile = getMyProfileEntity(userId)
        val profileId = profile.idOrThrow()
        val interests = profileInterestRepository.findByProfileId(profileId)
        val photos = profilePhotoRepository.findByProfileIdAndDeletedAtIsNullOrderByDisplayOrderAsc(profileId)

        if (profile.status !in setOf(ProfileStatus.DRAFT, ProfileStatus.REJECTED)) {
            throw DatingException(ErrorCode.CONFLICT, "Only DRAFT or REJECTED profiles can be submitted.")
        }
        if (profile.nickname.isNullOrBlank() || profile.regionCode.isNullOrBlank() || profile.bio.isNullOrBlank()) {
            throw DatingException(ErrorCode.INVALID_ARGUMENT, "Nickname, region, and bio are required.")
        }
        if (interests.size < 3) {
            throw DatingException(ErrorCode.INVALID_ARGUMENT, "At least 3 interests are required.")
        }
        if (photos.isEmpty()) {
            throw DatingException(ErrorCode.INVALID_ARGUMENT, "At least 1 profile photo is required.")
        }

        profile.status = ProfileStatus.PENDING_REVIEW
        return toResult(profile)
    }

    @Transactional
    fun uploadPhoto(userId: Long, request: UploadProfilePhotoRequest): ProfilePhotoResult {
        userAccessService.requireWritable(userId)
        val profile = getMyProfileEntity(userId)
        val profileId = profile.idOrThrow()
        val photos = profilePhotoRepository.findByProfileIdAndDeletedAtIsNullOrderByDisplayOrderAsc(profileId)

        if (photos.size >= 6) {
            throw DatingException(ErrorCode.INVALID_ARGUMENT, "Profile photos are limited to 6.")
        }

        val displayOrder = request.displayOrder
            ?: ((photos.maxOfOrNull { it.displayOrder.toInt() } ?: 0) + 1)
        if (displayOrder !in 1..6) {
            throw DatingException(ErrorCode.INVALID_ARGUMENT, "Display order must be between 1 and 6.")
        }
        if (photos.any { it.displayOrder.toInt() == displayOrder }) {
            throw DatingException(ErrorCode.CONFLICT, "Display order is already used.")
        }

        val photo = profilePhotoRepository.save(
            ProfilePhotoJpaEntity(
                profileId = profileId,
                fileKey = request.fileKey,
                displayOrder = displayOrder.toShort(),
                status = PhotoStatus.PENDING,
            ),
        )
        return photo.toResult()
    }

    @Transactional
    fun setPrimaryPhoto(userId: Long, photoId: Long): ProfilePhotoResult {
        userAccessService.requireWritable(userId)
        val profile = getMyProfileEntity(userId)
        val profileId = profile.idOrThrow()
        val photo = profilePhotoRepository.findByIdAndProfileIdAndDeletedAtIsNull(photoId, profileId)
            ?: throw DatingException(ErrorCode.NOT_FOUND, "Profile photo was not found.")

        if (photo.status != PhotoStatus.APPROVED) {
            throw DatingException(ErrorCode.CONFLICT, "Only APPROVED photos can be primary.")
        }

        profilePhotoRepository.findByProfileIdAndDeletedAtIsNullOrderByDisplayOrderAsc(profileId)
            .filter { it.isPrimary }
            .forEach { it.isPrimary = false }
        photo.isPrimary = true

        return photo.toResult()
    }

    @Transactional
    fun deletePhoto(userId: Long, photoId: Long): ProfilePhotoResult {
        userAccessService.requireWritable(userId)
        val profile = getMyProfileEntity(userId)
        val profileId = profile.idOrThrow()
        val photo = profilePhotoRepository.findByIdAndProfileIdAndDeletedAtIsNull(photoId, profileId)
            ?: throw DatingException(ErrorCode.NOT_FOUND, "Profile photo was not found.")

        val approvedPhotos = profilePhotoRepository
            .findByProfileIdAndDeletedAtIsNullOrderByDisplayOrderAsc(profileId)
            .filter { it.status == PhotoStatus.APPROVED }
        if (profile.status == ProfileStatus.ACTIVE && photo.status == PhotoStatus.APPROVED && approvedPhotos.size <= 1) {
            throw DatingException(ErrorCode.CONFLICT, "Active profiles must keep at least 1 approved photo.")
        }

        if (photo.isPrimary) {
            approvedPhotos
                .firstOrNull { it.id != photo.id }
                ?.isPrimary = true
        }
        photo.deletedAt = Instant.now()
        photo.status = PhotoStatus.DELETED
        photo.isPrimary = false
        return photo.toResult()
    }

    @Transactional
    fun updateVisibility(userId: Long, request: ProfileVisibilityRequest): ProfileResult {
        userAccessService.requireWritable(userId)
        val profile = getMyProfileEntity(userId)
        profile.status = when {
            request.hidden && profile.status == ProfileStatus.ACTIVE -> ProfileStatus.HIDDEN
            !request.hidden && profile.status == ProfileStatus.HIDDEN -> ProfileStatus.ACTIVE
            else -> throw DatingException(ErrorCode.CONFLICT, "Profile visibility can only transition between ACTIVE and HIDDEN.")
        }
        return toResult(profile)
    }

    private fun applyProfileFields(profile: ProfileJpaEntity, request: UpsertProfileRequest) {
        if (request.ageMin > request.ageMax) {
            throw DatingException(ErrorCode.INVALID_ARGUMENT, "ageMin must be less than or equal to ageMax.")
        }
        profile.nickname = request.nickname
        profile.regionCode = request.regionCode
        profile.bio = request.bio
    }

    private fun saveInterests(profileId: Long, codes: List<String>) {
        val normalizedCodes = codes.map { it.trim().uppercase() }.filter { it.isNotBlank() }.distinct()
        if (normalizedCodes.isEmpty()) {
            profileInterestRepository.deleteByProfileId(profileId)
            return
        }

        val interests = normalizedCodes.map { code ->
            interestRepository.findByCode(code)
                ?: interestRepository.save(InterestJpaEntity(code = code, label = code))
        }

        profileInterestRepository.deleteByProfileId(profileId)
        interests.forEach { interest ->
            profileInterestRepository.save(ProfileInterestJpaEntity(profileId = profileId, interestId = interest.idOrThrow()))
        }
    }

    private fun saveMatchPreferences(userId: Long, request: UpsertProfileRequest) {
        val preference = matchPreferenceRepository.findByUserId(userId)
            ?: matchPreferenceRepository.save(MatchPreferenceJpaEntity(userId = userId))
        preference.ageMin = request.ageMin.toShort()
        preference.ageMax = request.ageMax.toShort()

        val preferenceId = preference.idOrThrow()
        matchPreferenceRegionRepository.deleteByMatchPreferenceId(preferenceId)
        request.preferenceRegionCodes
            .map { it.trim().uppercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach {
                matchPreferenceRegionRepository.save(
                    MatchPreferenceRegionJpaEntity(matchPreferenceId = preferenceId, regionCode = it),
                )
            }
    }

    private fun getMyProfileEntity(userId: Long): ProfileJpaEntity {
        return profileRepository.findByUserIdAndDeletedAtIsNull(userId)
            ?: throw DatingException(ErrorCode.NOT_FOUND, "Profile was not found.")
    }

    private fun toResult(profile: ProfileJpaEntity): ProfileResult {
        val profileId = profile.idOrThrow()
        val photos = profilePhotoRepository
            .findByProfileIdAndDeletedAtIsNullOrderByDisplayOrderAsc(profileId)
            .map { it.toResult() }
        val interestLinks = profileInterestRepository.findByProfileId(profileId)
        val interestLabels = interestRepository
            .findAllById(interestLinks.map { it.interestId })
            .map { it.code }
            .sorted()

        return ProfileResult(
            id = profileId,
            userId = profile.userId,
            nickname = profile.nickname,
            gender = profile.gender,
            birthDate = profile.birthDate,
            regionCode = profile.regionCode,
            bio = profile.bio,
            status = profile.status,
            interests = interestLabels,
            photos = photos,
        )
    }

    private fun ProfilePhotoJpaEntity.toResult(): ProfilePhotoResult =
        ProfilePhotoResult(
            id = idOrThrow(),
            fileKey = fileKey,
            displayOrder = displayOrder.toInt(),
            primary = isPrimary,
            status = status,
        )

    private fun ProfileJpaEntity.idOrThrow(): Long = id ?: error("Persisted profile is missing id.")

    private fun ProfilePhotoJpaEntity.idOrThrow(): Long = id ?: error("Persisted profile photo is missing id.")

    private fun InterestJpaEntity.idOrThrow(): Long = id ?: error("Persisted interest is missing id.")

    private fun MatchPreferenceJpaEntity.idOrThrow(): Long = id ?: error("Persisted match preference is missing id.")
}

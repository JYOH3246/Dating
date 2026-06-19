package com.dating.modules.profile.application

import com.dating.modules.profile.domain.GenderType
import com.dating.modules.profile.domain.PhotoStatus
import com.dating.modules.profile.domain.ProfileStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class UpsertProfileRequest(
    @field:NotBlank
    val nickname: String,
    @field:NotBlank
    val regionCode: String,
    @field:NotBlank
    val bio: String,
    val interestCodes: List<String> = emptyList(),
    @field:Min(19)
    @field:Max(99)
    val ageMin: Int = 19,
    @field:Min(19)
    @field:Max(99)
    val ageMax: Int = 99,
    val preferenceRegionCodes: List<String> = emptyList(),
)

data class UploadProfilePhotoRequest(
    @field:NotBlank
    val fileKey: String,
    @field:Min(1)
    @field:Max(6)
    val displayOrder: Int? = null,
)

data class ProfileVisibilityRequest(
    val hidden: Boolean,
)

data class ProfileResult(
    val id: Long,
    val userId: Long,
    val nickname: String?,
    val gender: GenderType,
    val birthDate: LocalDate?,
    val regionCode: String?,
    val bio: String?,
    val status: ProfileStatus,
    val interests: List<String>,
    val photos: List<ProfilePhotoResult>,
)

data class ProfilePhotoResult(
    val id: Long,
    val fileKey: String,
    val displayOrder: Int,
    val primary: Boolean,
    val status: PhotoStatus,
)

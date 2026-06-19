package com.dating.modules.profile.presentation.rest

import com.dating.modules.profile.application.ProfilePhotoResult
import com.dating.modules.profile.application.ProfileResult
import com.dating.modules.profile.application.ProfileService
import com.dating.modules.profile.application.ProfileVisibilityRequest
import com.dating.modules.profile.application.UploadProfilePhotoRequest
import com.dating.modules.profile.application.UpsertProfileRequest
import com.dating.platform.security.CurrentUser
import com.dating.shared.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping
class ProfileController(
    private val profileService: ProfileService,
) {
    @PostMapping("/profiles")
    fun createProfile(
        @CurrentUser userId: Long,
        @Valid @RequestBody request: UpsertProfileRequest,
    ): ApiResponse<ProfileResult> {
        return ApiResponse.ok(profileService.createProfile(userId, request))
    }

    @GetMapping("/profiles/me")
    fun getMyProfile(@CurrentUser userId: Long): ApiResponse<ProfileResult> {
        return ApiResponse.ok(profileService.getMyProfile(userId))
    }

    @PatchMapping("/profiles/me")
    fun updateMyProfile(
        @CurrentUser userId: Long,
        @Valid @RequestBody request: UpsertProfileRequest,
    ): ApiResponse<ProfileResult> {
        return ApiResponse.ok(profileService.updateMyProfile(userId, request))
    }

    @PostMapping("/profiles/me/submit")
    fun submitMyProfile(@CurrentUser userId: Long): ApiResponse<ProfileResult> {
        return ApiResponse.ok(profileService.submitMyProfile(userId))
    }

    @PatchMapping("/profiles/me/visibility")
    fun updateVisibility(
        @CurrentUser userId: Long,
        @RequestBody request: ProfileVisibilityRequest,
    ): ApiResponse<ProfileResult> {
        return ApiResponse.ok(profileService.updateVisibility(userId, request))
    }

    @PostMapping("/profiles/me/photos")
    fun uploadPhoto(
        @CurrentUser userId: Long,
        @Valid @RequestBody request: UploadProfilePhotoRequest,
    ): ApiResponse<ProfilePhotoResult> {
        return ApiResponse.ok(profileService.uploadPhoto(userId, request))
    }

    @PatchMapping("/profiles/me/photos/{id}/primary")
    fun setPrimaryPhoto(
        @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ApiResponse<ProfilePhotoResult> {
        return ApiResponse.ok(profileService.setPrimaryPhoto(userId, id))
    }

    @DeleteMapping("/profiles/me/photos/{id}")
    fun deletePhoto(
        @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ApiResponse<ProfilePhotoResult> {
        return ApiResponse.ok(profileService.deletePhoto(userId, id))
    }
}

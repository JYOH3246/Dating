package com.dating.modules.notification.presentation.rest

import com.dating.modules.notification.application.NotificationSettingsRequest
import com.dating.modules.notification.application.NotificationSettingsResult
import com.dating.modules.notification.application.NotificationSettingsService
import com.dating.platform.security.CurrentUser
import com.dating.shared.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/notifications")
class NotificationController(
    private val notificationSettingsService: NotificationSettingsService,
) {
    @GetMapping("/settings")
    fun getSettings(@CurrentUser userId: Long): ApiResponse<NotificationSettingsResult> {
        return ApiResponse.ok(notificationSettingsService.getSettings(userId))
    }

    @PatchMapping("/settings")
    fun updateSettings(
        @CurrentUser userId: Long,
        @RequestBody request: NotificationSettingsRequest,
    ): ApiResponse<NotificationSettingsResult> {
        return ApiResponse.ok(notificationSettingsService.updateSettings(userId, request))
    }
}

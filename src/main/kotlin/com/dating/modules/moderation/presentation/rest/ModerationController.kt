package com.dating.modules.moderation.presentation.rest

import com.dating.modules.moderation.application.CreateReportRequest
import com.dating.modules.moderation.application.ModerationService
import com.dating.modules.moderation.application.ReportResult
import com.dating.platform.security.CurrentUser
import com.dating.shared.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/reports")
class ModerationController(
    private val moderationService: ModerationService,
) {
    @PostMapping
    fun createReport(
        @CurrentUser userId: Long,
        @Valid @RequestBody request: CreateReportRequest,
    ): ApiResponse<ReportResult> {
        return ApiResponse.ok(moderationService.createReport(userId, request))
    }
}

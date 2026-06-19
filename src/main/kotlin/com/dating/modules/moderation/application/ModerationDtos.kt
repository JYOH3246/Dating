package com.dating.modules.moderation.application

import com.dating.modules.moderation.domain.ReportReason
import com.dating.modules.moderation.domain.ReportStatus
import com.dating.modules.moderation.domain.ReportTargetType
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreateReportRequest(
    val targetType: ReportTargetType,
    @field:NotNull
    val targetId: Long,
    val reason: ReportReason,
    @field:Size(max = 2000)
    val reasonDetail: String? = null,
)

data class ReportResult(
    val id: Long,
    val reporterUserId: Long,
    val targetType: ReportTargetType,
    val targetId: Long,
    val reason: ReportReason,
    val reasonDetail: String?,
    val status: ReportStatus,
    val createdAt: Instant?,
)

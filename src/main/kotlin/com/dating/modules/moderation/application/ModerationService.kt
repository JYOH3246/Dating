package com.dating.modules.moderation.application

import com.dating.modules.identity.application.UserAccessService
import com.dating.modules.moderation.domain.ReportStatus
import com.dating.modules.moderation.infrastructure.jpa.ReportJpaEntity
import com.dating.modules.moderation.infrastructure.jpa.ReportJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ModerationService(
    private val userAccessService: UserAccessService,
    private val reportRepository: ReportJpaRepository,
) {
    @Transactional
    fun createReport(userId: Long, request: CreateReportRequest): ReportResult {
        userAccessService.requireActive(userId)
        val report = reportRepository.save(
            ReportJpaEntity(
                reporterUserId = userId,
                targetType = request.targetType,
                targetId = request.targetId,
                reason = request.reason,
                reasonDetail = request.reasonDetail,
                status = ReportStatus.RECEIVED,
            ),
        )
        return report.toResult()
    }

    private fun ReportJpaEntity.toResult(): ReportResult =
        ReportResult(
            id = id ?: error("Persisted report is missing id."),
            reporterUserId = reporterUserId,
            targetType = targetType,
            targetId = targetId,
            reason = reason,
            reasonDetail = reasonDetail,
            status = status,
            createdAt = createdAt,
        )
}

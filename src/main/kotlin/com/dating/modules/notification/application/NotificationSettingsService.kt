package com.dating.modules.notification.application

import com.dating.modules.identity.application.UserAccessService
import com.dating.modules.notification.infrastructure.jpa.NotificationSettingJpaEntity
import com.dating.modules.notification.infrastructure.jpa.NotificationSettingJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class NotificationSettingsService(
    private val userAccessService: UserAccessService,
    private val notificationSettingRepository: NotificationSettingJpaRepository,
) {
    @Transactional(readOnly = true)
    fun getSettings(userId: Long): NotificationSettingsResult {
        userAccessService.requireActive(userId)
        return (notificationSettingRepository.findByUserId(userId) ?: NotificationSettingJpaEntity(userId = userId))
            .toResult()
    }

    @Transactional
    fun updateSettings(userId: Long, request: NotificationSettingsRequest): NotificationSettingsResult {
        userAccessService.requireActive(userId)
        val settings = notificationSettingRepository.findByUserId(userId)
            ?: NotificationSettingJpaEntity(userId = userId)

        settings.pushMessage = request.pushMessage
        settings.pushMatch = request.pushMatch
        settings.pushLike = request.pushLike
        settings.marketing = request.marketing
        settings.nightMarketing = request.nightMarketing
        settings.updatedAt = Instant.now()

        return notificationSettingRepository.save(settings).toResult()
    }

    private fun NotificationSettingJpaEntity.toResult(): NotificationSettingsResult =
        NotificationSettingsResult(
            userId = userId,
            pushMessage = pushMessage,
            pushMatch = pushMatch,
            pushLike = pushLike,
            marketing = marketing,
            nightMarketing = nightMarketing,
        )
}

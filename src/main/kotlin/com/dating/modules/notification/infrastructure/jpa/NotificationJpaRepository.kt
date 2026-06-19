package com.dating.modules.notification.infrastructure.jpa

import org.springframework.data.jpa.repository.JpaRepository

interface NotificationSettingJpaRepository : JpaRepository<NotificationSettingJpaEntity, Long> {
    fun findByUserId(userId: Long): NotificationSettingJpaEntity?
}

package com.dating.modules.notification.infrastructure.jpa

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "notification_settings")
class NotificationSettingJpaEntity(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "push_message", nullable = false)
    var pushMessage: Boolean = true,

    @Column(name = "push_match", nullable = false)
    var pushMatch: Boolean = true,

    @Column(name = "push_like", nullable = false)
    var pushLike: Boolean = true,

    @Column(name = "marketing", nullable = false)
    var marketing: Boolean = false,

    @Column(name = "night_marketing", nullable = false)
    var nightMarketing: Boolean = false,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

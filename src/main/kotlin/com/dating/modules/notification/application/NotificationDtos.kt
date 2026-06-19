package com.dating.modules.notification.application

data class NotificationSettingsRequest(
    val pushMessage: Boolean,
    val pushMatch: Boolean,
    val pushLike: Boolean,
    val marketing: Boolean,
    val nightMarketing: Boolean,
)

data class NotificationSettingsResult(
    val userId: Long,
    val pushMessage: Boolean,
    val pushMatch: Boolean,
    val pushLike: Boolean,
    val marketing: Boolean,
    val nightMarketing: Boolean,
)

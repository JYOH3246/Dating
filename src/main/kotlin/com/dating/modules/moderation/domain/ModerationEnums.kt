package com.dating.modules.moderation.domain

enum class ReportTargetType {
    USER,
    PROFILE,
    PHOTO,
    MESSAGE,
    CHAT_ROOM,
}

enum class ReportStatus {
    RECEIVED,
    UNDER_REVIEW,
    ACTION_TAKEN,
    REJECTED,
    CLOSED,
}

enum class ReportReason {
    INAPPROPRIATE_PHOTO,
    ABUSIVE_LANGUAGE,
    HARASSMENT,
    SPAM,
    SCAM,
    IMPERSONATION,
    SEXUAL_CONTENT,
    ILLEGAL_CONTENT,
    PRIVACY_VIOLATION,
    OTHER,
}

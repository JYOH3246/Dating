package com.dating.modules.profile.domain

enum class GenderType {
    MALE,
    FEMALE,
    UNKNOWN,
}

enum class ProfileStatus {
    DRAFT,
    PENDING_REVIEW,
    ACTIVE,
    REJECTED,
    HIDDEN,
    DELETED,
}

enum class PhotoStatus {
    PENDING,
    APPROVED,
    REJECTED,
    DELETED,
}

package com.dating.shared.response

data class CursorResponse<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasNext: Boolean,
)

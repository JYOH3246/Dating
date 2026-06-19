package com.dating.shared.response

data class ErrorResponse(
    val success: Boolean = false,
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap(),
)

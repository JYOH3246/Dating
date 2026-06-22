package com.dating.platform.security

data class AuthPrincipal(
    val userId: Long,
    val sessionId: Long,
)

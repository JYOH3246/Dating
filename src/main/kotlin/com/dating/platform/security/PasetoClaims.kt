package com.dating.platform.security

import java.time.Instant

data class PasetoClaims(
    val iss: String,
    val sub: String,
    val sid: Long,
    val typ: TokenType,
    val kid: String,
    val iat: Instant,
    val exp: Instant,
    val jti: String,
)

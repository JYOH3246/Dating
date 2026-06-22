package com.dating.platform.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "dating.auth")
data class AuthTokenProperties(
    val issuer: String,
    val accessTokenTtl: Duration,
    val refreshTokenTtl: Duration,
    val maxActiveSessions: Int,
    val refreshTokenPepper: String,
    val paseto: PasetoProperties,
)

data class PasetoProperties(
    val keyId: String,
    val privateKeyBase64: String = "",
    val publicKeyBase64: String = "",
    val allowDevGeneratedKey: Boolean = false,
)

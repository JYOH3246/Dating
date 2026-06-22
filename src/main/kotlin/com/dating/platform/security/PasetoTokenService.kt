package com.dating.platform.security

import com.dating.modules.identity.infrastructure.jpa.AuthTokenSessionJpaRepository
import com.dating.shared.exception.DatingException
import com.dating.shared.exception.ErrorCode
import org.paseto4j.version4.Paseto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.security.SignatureException
import java.time.Instant
import java.util.UUID

data class IssuedToken(
    val token: String,
    val claims: PasetoClaims,
)

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val refreshExpiresIn: Long,
)

@Service
class PasetoTokenService(
    private val properties: AuthTokenProperties,
    private val keyProvider: PasetoKeyProvider,
    private val objectMapper: ObjectMapper,
    private val sessionRepository: AuthTokenSessionJpaRepository,
) {
    fun issueAccessToken(userId: Long, sessionId: Long): IssuedToken {
        return issueToken(userId = userId, sessionId = sessionId, type = TokenType.ACCESS, ttl = properties.accessTokenTtl)
    }

    fun issueRefreshToken(userId: Long, sessionId: Long): IssuedToken {
        return issueToken(userId = userId, sessionId = sessionId, type = TokenType.REFRESH, ttl = properties.refreshTokenTtl)
    }

    fun issueTokenPair(userId: Long, sessionId: Long): Pair<TokenPair, IssuedToken> {
        val accessToken = issueAccessToken(userId, sessionId)
        val refreshToken = issueRefreshToken(userId, sessionId)
        return TokenPair(
            accessToken = accessToken.token,
            refreshToken = refreshToken.token,
            expiresIn = properties.accessTokenTtl.seconds,
            refreshExpiresIn = properties.refreshTokenTtl.seconds,
        ) to refreshToken
    }

    fun parse(token: String, expectedType: TokenType): PasetoClaims {
        val key = keyProvider.activeKey()
        val payload = try {
            Paseto.parse(key.publicKey, token)
        } catch (exception: SignatureException) {
            throw DatingException(ErrorCode.UNAUTHENTICATED, "Invalid token signature.")
        } catch (exception: RuntimeException) {
            throw DatingException(ErrorCode.UNAUTHENTICATED, "Invalid token.")
        }

        val claims = runCatching { objectMapper.readValue(payload, PasetoClaims::class.java) }
            .getOrElse { throw DatingException(ErrorCode.UNAUTHENTICATED, "Invalid token payload.") }

        if (claims.iss != properties.issuer || claims.typ != expectedType || claims.kid != key.keyId) {
            throw DatingException(ErrorCode.UNAUTHENTICATED, "Invalid token claims.")
        }
        if (!claims.exp.isAfter(Instant.now())) {
            throw DatingException(ErrorCode.UNAUTHENTICATED, "Token has expired.")
        }
        return claims
    }

    @Transactional(readOnly = true)
    fun authenticateAccessToken(accessToken: String): AuthPrincipal {
        val claims = parse(accessToken, TokenType.ACCESS)
        val userId = claims.sub.toLongOrNull()
            ?: throw DatingException(ErrorCode.UNAUTHENTICATED, "Invalid token subject.")
        val session = sessionRepository.findByIdAndRevokedAtIsNull(claims.sid)
            ?: throw DatingException(ErrorCode.UNAUTHENTICATED, "Session is revoked.")

        if (session.userId != userId || !session.expiresAt.isAfter(Instant.now())) {
            throw DatingException(ErrorCode.UNAUTHENTICATED, "Session is expired or invalid.")
        }

        return AuthPrincipal(userId = userId, sessionId = session.id ?: claims.sid)
    }

    private fun issueToken(userId: Long, sessionId: Long, type: TokenType, ttl: java.time.Duration): IssuedToken {
        val now = Instant.now()
        val key = keyProvider.activeKey()
        val claims = PasetoClaims(
            iss = properties.issuer,
            sub = userId.toString(),
            sid = sessionId,
            typ = type,
            kid = key.keyId,
            iat = now,
            exp = now.plus(ttl),
            jti = UUID.randomUUID().toString(),
        )
        val payload = objectMapper.writeValueAsString(claims)
        return IssuedToken(token = Paseto.sign(key.privateKey, payload), claims = claims)
    }
}

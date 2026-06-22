package com.dating.modules.identity.presentation.rest

import com.dating.modules.identity.application.AgreementsRequest
import com.dating.modules.identity.application.AuthSessionInfo
import com.dating.modules.identity.application.AuthSessionResult
import com.dating.modules.identity.application.AuthService
import com.dating.modules.identity.application.AuthUserResult
import com.dating.modules.identity.application.IdentityVerificationRequest
import com.dating.modules.identity.application.OAuthLoginRequest
import com.dating.modules.identity.application.RefreshTokenRequest
import com.dating.modules.identity.application.TokenRefreshResult
import com.dating.platform.security.PasetoTokenService
import com.dating.platform.security.CurrentUser
import com.dating.shared.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val pasetoTokenService: PasetoTokenService,
) {
    @PostMapping("/oauth/{provider}")
    fun oauthLogin(
        @PathVariable provider: String,
        @Valid @RequestBody request: OAuthLoginRequest,
    ): ApiResponse<AuthSessionResult> {
        return ApiResponse.ok(authService.oauthLogin(provider, request))
    }

    @PostMapping("/identity/verify")
    fun verifyIdentity(
        @CurrentUser userId: Long,
        @Valid @RequestBody request: IdentityVerificationRequest,
    ): ApiResponse<AuthUserResult> {
        return ApiResponse.ok(authService.verifyIdentity(userId, request))
    }

    @PostMapping("/agreements")
    fun acceptAgreements(
        @CurrentUser userId: Long,
        @Valid @RequestBody request: AgreementsRequest,
    ): ApiResponse<AuthUserResult> {
        return ApiResponse.ok(authService.acceptAgreements(userId, request))
    }

    @PostMapping("/token/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): ApiResponse<TokenRefreshResult> {
        return ApiResponse.ok(authService.refresh(request))
    }

    @PostMapping("/logout")
    fun logout(
        @CurrentUser userId: Long,
        @RequestHeader("Authorization") authorization: String,
    ): ApiResponse<Unit> {
        val principal = pasetoTokenService.authenticateAccessToken(extractBearerToken(authorization))
        authService.logout(userId = userId, sessionId = principal.sessionId)
        return ApiResponse.empty()
    }

    @GetMapping("/sessions")
    fun getSessions(@CurrentUser userId: Long): ApiResponse<List<AuthSessionInfo>> {
        return ApiResponse.ok(authService.getSessions(userId))
    }

    @DeleteMapping("/sessions/{id}")
    fun revokeSession(
        @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ApiResponse<Unit> {
        authService.revokeSession(userId, id)
        return ApiResponse.empty()
    }

    private fun extractBearerToken(authorization: String): String {
        val prefix = "Bearer "
        if (!authorization.startsWith(prefix, ignoreCase = true)) {
            throw com.dating.shared.exception.DatingException(
                com.dating.shared.exception.ErrorCode.UNAUTHENTICATED,
                "Authorization must use Bearer token.",
            )
        }
        return authorization.substring(prefix.length).trim()
    }
}

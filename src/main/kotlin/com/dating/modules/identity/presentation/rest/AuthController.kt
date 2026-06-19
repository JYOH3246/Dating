package com.dating.modules.identity.presentation.rest

import com.dating.modules.identity.application.AgreementsRequest
import com.dating.modules.identity.application.AuthService
import com.dating.modules.identity.application.AuthUserResult
import com.dating.modules.identity.application.IdentityVerificationRequest
import com.dating.modules.identity.application.OAuthLoginRequest
import com.dating.platform.security.CurrentUser
import com.dating.shared.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/oauth/{provider}")
    fun oauthLogin(
        @PathVariable provider: String,
        @Valid @RequestBody request: OAuthLoginRequest,
    ): ApiResponse<AuthUserResult> {
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
}

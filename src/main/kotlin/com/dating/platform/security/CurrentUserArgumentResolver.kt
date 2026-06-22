package com.dating.platform.security

import com.dating.shared.exception.DatingException
import com.dating.shared.exception.ErrorCode
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class CurrentUserArgumentResolver(
    private val pasetoTokenService: PasetoTokenService,
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(CurrentUser::class.java) &&
            parameter.parameterType == Long::class.java
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        val header = webRequest.getHeader("Authorization")
            ?: throw DatingException(ErrorCode.UNAUTHENTICATED, "Authorization header is required.")

        if (!header.startsWith(BEARER_PREFIX, ignoreCase = true)) {
            throw DatingException(ErrorCode.UNAUTHENTICATED, "Authorization must use Bearer token.")
        }

        val token = header.substring(BEARER_PREFIX.length).trim()
        if (token.isBlank()) {
            throw DatingException(ErrorCode.UNAUTHENTICATED, "Bearer token is empty.")
        }

        return pasetoTokenService.authenticateAccessToken(token).userId
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
    }
}

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
class CurrentUserArgumentResolver : HandlerMethodArgumentResolver {

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
        val header = webRequest.getHeader("X-User-Id")
            ?: throw DatingException(ErrorCode.UNAUTHENTICATED, "X-User-Id header is required until token auth is wired.")

        return header.toLongOrNull()
            ?: throw DatingException(ErrorCode.UNAUTHENTICATED, "X-User-Id must be a numeric user id.")
    }
}

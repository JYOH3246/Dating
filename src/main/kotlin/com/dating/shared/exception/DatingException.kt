package com.dating.shared.exception

class DatingException(
    val code: ErrorCode,
    override val message: String = code.defaultMessage,
) : RuntimeException(message)

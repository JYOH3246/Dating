package com.dating.shared.response

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String? = null,
) {
    companion object {
        fun <T> ok(data: T, message: String? = null): ApiResponse<T> =
            ApiResponse(success = true, data = data, message = message)

        fun empty(message: String? = null): ApiResponse<Unit> =
            ApiResponse(success = true, data = Unit, message = message)
    }
}

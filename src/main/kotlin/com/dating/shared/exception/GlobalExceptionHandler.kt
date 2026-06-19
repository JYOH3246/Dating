package com.dating.shared.exception

import com.dating.shared.response.ErrorResponse
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(DatingException::class)
    fun handleDatingException(exception: DatingException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(exception.code.status)
            .body(
                ErrorResponse(
                    code = exception.code.name,
                    message = exception.message,
                ),
            )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = exception.bindingResult.fieldErrors.associate { error ->
            error.field to (error.defaultMessage ?: "Invalid value.")
        }

        return ResponseEntity
            .badRequest()
            .body(
                ErrorResponse(
                    code = ErrorCode.INVALID_ARGUMENT.name,
                    message = ErrorCode.INVALID_ARGUMENT.defaultMessage,
                    details = details,
                ),
            )
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrity(exception: DataIntegrityViolationException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(ErrorCode.CONFLICT.status)
            .body(
                ErrorResponse(
                    code = ErrorCode.CONFLICT.name,
                    message = "Request violates a uniqueness or integrity rule.",
                ),
            )
    }
}

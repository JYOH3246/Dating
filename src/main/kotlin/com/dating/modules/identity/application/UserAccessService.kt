package com.dating.modules.identity.application

import com.dating.modules.identity.domain.UserStatus
import com.dating.modules.identity.infrastructure.jpa.UserJpaEntity
import com.dating.modules.identity.infrastructure.jpa.UserJpaRepository
import com.dating.shared.exception.DatingException
import com.dating.shared.exception.ErrorCode
import org.springframework.stereotype.Service

@Service
class UserAccessService(
    private val userRepository: UserJpaRepository,
) {
    fun getUser(userId: Long): UserJpaEntity {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
            ?: throw DatingException(ErrorCode.NOT_FOUND, "User was not found.")
    }

    fun requireActive(userId: Long): UserJpaEntity {
        val user = getUser(userId)
        if (user.status != UserStatus.ACTIVE) {
            throw DatingException(ErrorCode.PERMISSION_DENIED, "User must be ACTIVE.")
        }
        return user
    }

    fun requireWritable(userId: Long): UserJpaEntity {
        val user = requireActive(userId)
        if (user.status == UserStatus.SUSPENDED || user.status == UserStatus.BANNED) {
            throw DatingException(ErrorCode.PERMISSION_DENIED, "User is not allowed to write.")
        }
        return user
    }
}

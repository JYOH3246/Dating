package com.dating.modules.chat.application

import com.dating.modules.chat.domain.ChatRoomStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class ChatRoomResult(
    val id: Long,
    val matchId: Long,
    val status: ChatRoomStatus,
    val memberUserIds: List<Long>,
)

data class SendMessageRequest(
    @field:NotBlank
    val clientMessageId: String,
    @field:NotBlank
    @field:Size(max = 2000)
    val content: String,
)

data class ChatMessageResult(
    val id: Long,
    val chatRoomId: Long,
    val senderUserId: Long,
    val clientMessageId: String,
    val content: String?,
    val createdAt: Instant,
)

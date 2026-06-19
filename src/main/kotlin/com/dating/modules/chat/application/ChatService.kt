package com.dating.modules.chat.application

import com.dating.modules.chat.domain.ChatRoomStatus
import com.dating.modules.chat.infrastructure.jpa.ChatMessageJpaEntity
import com.dating.modules.chat.infrastructure.jpa.ChatMessageJpaRepository
import com.dating.modules.chat.infrastructure.jpa.ChatRoomJpaEntity
import com.dating.modules.chat.infrastructure.jpa.ChatRoomJpaRepository
import com.dating.modules.chat.infrastructure.jpa.ChatRoomMemberJpaRepository
import com.dating.modules.identity.application.UserAccessService
import com.dating.modules.interaction.infrastructure.jpa.UserBlockJpaRepository
import com.dating.modules.match.domain.MatchStatus
import com.dating.modules.match.infrastructure.jpa.MatchJpaRepository
import com.dating.shared.exception.DatingException
import com.dating.shared.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChatService(
    private val userAccessService: UserAccessService,
    private val chatRoomRepository: ChatRoomJpaRepository,
    private val chatRoomMemberRepository: ChatRoomMemberJpaRepository,
    private val chatMessageRepository: ChatMessageJpaRepository,
    private val matchRepository: MatchJpaRepository,
    private val blockRepository: UserBlockJpaRepository,
) {
    @Transactional(readOnly = true)
    fun getRooms(userId: Long): List<ChatRoomResult> {
        userAccessService.requireActive(userId)
        val roomIds = chatRoomMemberRepository.findByUserId(userId).map { it.chatRoomId }
        return chatRoomRepository.findAllById(roomIds).map { room -> room.toResult() }
    }

    @Transactional(readOnly = true)
    fun getMessages(userId: Long, roomId: Long): List<ChatMessageResult> {
        userAccessService.requireActive(userId)
        requireRoomMember(roomId, userId)
        return chatMessageRepository.findByChatRoomIdOrderByCreatedAtAsc(roomId)
            .map { it.toResult() }
    }

    @Transactional
    fun sendMessage(userId: Long, roomId: Long, request: SendMessageRequest): ChatMessageResult {
        userAccessService.requireWritable(userId)
        val room = chatRoomRepository.findById(roomId)
            .orElseThrow { DatingException(ErrorCode.NOT_FOUND, "Chat room was not found.") }
        requireRoomMember(roomId, userId)
        requireChatOpen(userId, room)

        chatMessageRepository.findByChatRoomIdAndClientMessageId(roomId, request.clientMessageId)
            ?.let { return it.toResult() }

        val message = chatMessageRepository.save(
            ChatMessageJpaEntity(
                chatRoomId = roomId,
                senderUserId = userId,
                clientMessageId = request.clientMessageId,
                content = request.content,
            ),
        )
        return message.toResult()
    }

    private fun requireRoomMember(roomId: Long, userId: Long) {
        if (!chatRoomMemberRepository.existsByChatRoomIdAndUserId(roomId, userId)) {
            throw DatingException(ErrorCode.PERMISSION_DENIED, "You are not a member of this chat room.")
        }
    }

    private fun requireChatOpen(userId: Long, room: ChatRoomJpaEntity) {
        if (room.status != ChatRoomStatus.OPEN) {
            throw DatingException(ErrorCode.CONFLICT, "Chat room is closed.")
        }
        val match = matchRepository.findById(room.matchId)
            .orElseThrow { DatingException(ErrorCode.NOT_FOUND, "Match was not found.") }
        if (match.status != MatchStatus.ACTIVE) {
            throw DatingException(ErrorCode.CONFLICT, "Match is not active.")
        }

        chatRoomMemberRepository.findByChatRoomId(room.idOrThrow())
            .map { it.userId }
            .filter { it != userId }
            .forEach { otherUserId ->
                if (blockRepository.existsBetween(userId, otherUserId)) {
                    throw DatingException(ErrorCode.PERMISSION_DENIED, "Blocked users cannot chat.")
                }
                userAccessService.requireActive(otherUserId)
            }
    }

    private fun ChatRoomJpaEntity.toResult(): ChatRoomResult {
        val roomId = idOrThrow()
        return ChatRoomResult(
            id = roomId,
            matchId = matchId,
            status = status,
            memberUserIds = chatRoomMemberRepository.findByChatRoomId(roomId).map { it.userId },
        )
    }

    private fun ChatMessageJpaEntity.toResult(): ChatMessageResult =
        ChatMessageResult(
            id = id ?: error("Persisted message is missing id."),
            chatRoomId = chatRoomId,
            senderUserId = senderUserId,
            clientMessageId = clientMessageId,
            content = content,
            createdAt = createdAt,
        )

    private fun ChatRoomJpaEntity.idOrThrow(): Long = id ?: error("Persisted chat room is missing id.")
}

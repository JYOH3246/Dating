package com.dating.modules.chat.infrastructure.jpa

import org.springframework.data.jpa.repository.JpaRepository

interface ChatRoomJpaRepository : JpaRepository<ChatRoomJpaEntity, Long> {
    fun findByMatchId(matchId: Long): ChatRoomJpaEntity?
}

interface ChatRoomMemberJpaRepository : JpaRepository<ChatRoomMemberJpaEntity, Long> {
    fun findByUserId(userId: Long): List<ChatRoomMemberJpaEntity>

    fun findByChatRoomId(chatRoomId: Long): List<ChatRoomMemberJpaEntity>

    fun existsByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): Boolean
}

interface ChatMessageJpaRepository : JpaRepository<ChatMessageJpaEntity, Long> {
    fun findByChatRoomIdOrderByCreatedAtAsc(chatRoomId: Long): List<ChatMessageJpaEntity>

    fun findByChatRoomIdAndClientMessageId(chatRoomId: Long, clientMessageId: String): ChatMessageJpaEntity?
}

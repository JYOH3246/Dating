package com.dating.modules.chat.infrastructure.jpa

import com.dating.modules.chat.domain.ChatRoomStatus
import com.dating.platform.persistence.BaseJpaEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "chat_rooms")
class ChatRoomJpaEntity(
    @Column(name = "match_id", nullable = false)
    var matchId: Long,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "chat_room_status")
    var status: ChatRoomStatus = ChatRoomStatus.OPEN,
) : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

@Entity
@Table(name = "chat_room_members")
class ChatRoomMemberJpaEntity(
    @Column(name = "chat_room_id", nullable = false)
    var chatRoomId: Long,

    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(name = "joined_at", nullable = false)
    var joinedAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

@Entity
@Table(name = "chat_messages")
class ChatMessageJpaEntity(
    @Column(name = "chat_room_id", nullable = false)
    var chatRoomId: Long,

    @Column(name = "sender_user_id", nullable = false)
    var senderUserId: Long,

    @Column(name = "client_message_id", nullable = false)
    var clientMessageId: String,

    @Column(name = "content")
    var content: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

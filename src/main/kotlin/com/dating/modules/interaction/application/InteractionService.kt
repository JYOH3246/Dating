package com.dating.modules.interaction.application

import com.dating.modules.chat.domain.ChatRoomStatus
import com.dating.modules.chat.infrastructure.jpa.ChatRoomJpaEntity
import com.dating.modules.chat.infrastructure.jpa.ChatRoomJpaRepository
import com.dating.modules.chat.infrastructure.jpa.ChatRoomMemberJpaEntity
import com.dating.modules.chat.infrastructure.jpa.ChatRoomMemberJpaRepository
import com.dating.modules.identity.application.UserAccessService
import com.dating.modules.interaction.domain.ReactionStatus
import com.dating.modules.interaction.domain.ReactionType
import com.dating.modules.interaction.infrastructure.jpa.UserBlockJpaEntity
import com.dating.modules.interaction.infrastructure.jpa.UserBlockJpaRepository
import com.dating.modules.interaction.infrastructure.jpa.UserReactionJpaEntity
import com.dating.modules.interaction.infrastructure.jpa.UserReactionJpaRepository
import com.dating.modules.match.domain.MatchStatus
import com.dating.modules.match.infrastructure.jpa.MatchJpaEntity
import com.dating.modules.match.infrastructure.jpa.MatchJpaRepository
import com.dating.shared.exception.DatingException
import com.dating.shared.exception.ErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class InteractionService(
    private val userAccessService: UserAccessService,
    private val reactionRepository: UserReactionJpaRepository,
    private val matchRepository: MatchJpaRepository,
    private val chatRoomRepository: ChatRoomJpaRepository,
    private val chatRoomMemberRepository: ChatRoomMemberJpaRepository,
    private val blockRepository: UserBlockJpaRepository,
) {
    @Transactional
    fun react(actorUserId: Long, request: ReactionRequest): ReactionResult {
        userAccessService.requireWritable(actorUserId)
        userAccessService.requireActive(request.targetUserId)
        if (actorUserId == request.targetUserId) {
            throw DatingException(ErrorCode.INVALID_ARGUMENT, "You cannot react to yourself.")
        }
        if (blockRepository.existsBetween(actorUserId, request.targetUserId)) {
            throw DatingException(ErrorCode.PERMISSION_DENIED, "Blocked users cannot interact.")
        }

        reactionRepository
            .findByActorUserIdAndTargetUserIdAndStatus(actorUserId, request.targetUserId, ReactionStatus.ACTIVE)
            ?.let { existing ->
                val existingMatch = findMatch(actorUserId, request.targetUserId)
                val existingRoom = existingMatch?.id?.let { chatRoomRepository.findByMatchId(it) }
                return ReactionResult(
                    reactionId = existing.idOrThrow(),
                    reactionType = existing.reactionType,
                    matched = existingMatch?.status == MatchStatus.ACTIVE,
                    matchId = existingMatch?.id,
                    chatRoomId = existingRoom?.id,
                )
            }

        val reaction = reactionRepository.save(
            UserReactionJpaEntity(
                actorUserId = actorUserId,
                targetUserId = request.targetUserId,
                reactionType = request.reactionType,
                expiresAt = if (request.reactionType == ReactionType.PASS) Instant.now().plusSeconds(PASS_EXPIRE_SECONDS) else null,
            ),
        )

        if (request.reactionType != ReactionType.LIKE) {
            return ReactionResult(
                reactionId = reaction.idOrThrow(),
                reactionType = reaction.reactionType,
                matched = false,
                matchId = null,
                chatRoomId = null,
            )
        }

        val reciprocalLike = reactionRepository.existsByActorUserIdAndTargetUserIdAndReactionTypeAndStatus(
            actorUserId = request.targetUserId,
            targetUserId = actorUserId,
            reactionType = ReactionType.LIKE,
            status = ReactionStatus.ACTIVE,
        )
        if (!reciprocalLike) {
            return ReactionResult(
                reactionId = reaction.idOrThrow(),
                reactionType = reaction.reactionType,
                matched = false,
                matchId = null,
                chatRoomId = null,
            )
        }

        val match = getOrCreateMatch(actorUserId, request.targetUserId)
        val chatRoom = getOrCreateChatRoom(match)

        return ReactionResult(
            reactionId = reaction.idOrThrow(),
            reactionType = reaction.reactionType,
            matched = true,
            matchId = match.id,
            chatRoomId = chatRoom.id,
        )
    }

    @Transactional(readOnly = true)
    fun getMatches(userId: Long): List<MatchResult> {
        userAccessService.requireActive(userId)
        return matchRepository.findByParticipantAndStatus(userId, MatchStatus.ACTIVE)
            .map { match ->
                val chatRoom = chatRoomRepository.findByMatchId(match.idOrThrow())
                MatchResult(
                    id = match.idOrThrow(),
                    counterpartUserId = if (match.user1Id == userId) match.user2Id else match.user1Id,
                    status = match.status,
                    chatRoomId = chatRoom?.id,
                    matchedAt = match.matchedAt,
                )
            }
    }

    @Transactional
    fun unmatch(userId: Long, matchId: Long): MatchResult {
        userAccessService.requireWritable(userId)
        val match = matchRepository.findById(matchId)
            .orElseThrow { DatingException(ErrorCode.NOT_FOUND, "Match was not found.") }
        if (match.user1Id != userId && match.user2Id != userId) {
            throw DatingException(ErrorCode.PERMISSION_DENIED, "You are not a participant in this match.")
        }
        match.status = MatchStatus.UNMATCHED
        match.unmatchedAt = Instant.now()

        val chatRoom = chatRoomRepository.findByMatchId(match.idOrThrow())
        chatRoom?.status = ChatRoomStatus.CLOSED

        return MatchResult(
            id = match.idOrThrow(),
            counterpartUserId = if (match.user1Id == userId) match.user2Id else match.user1Id,
            status = match.status,
            chatRoomId = chatRoom?.id,
            matchedAt = match.matchedAt,
        )
    }

    @Transactional
    fun block(userId: Long, request: BlockRequest): BlockResult {
        userAccessService.requireWritable(userId)
        userAccessService.requireActive(request.blockedUserId)
        if (userId == request.blockedUserId) {
            throw DatingException(ErrorCode.INVALID_ARGUMENT, "You cannot block yourself.")
        }

        val block = blockRepository.findByBlockerUserIdAndBlockedUserId(userId, request.blockedUserId)
            ?: blockRepository.save(UserBlockJpaEntity(blockerUserId = userId, blockedUserId = request.blockedUserId))
        return block.toResult()
    }

    @Transactional
    fun unblock(userId: Long, blockId: Long) {
        userAccessService.requireWritable(userId)
        val block = blockRepository.findByIdAndBlockerUserId(blockId, userId)
            ?: throw DatingException(ErrorCode.NOT_FOUND, "Block was not found.")
        blockRepository.delete(block)
    }

    private fun getOrCreateMatch(userA: Long, userB: Long): MatchJpaEntity {
        val (user1, user2) = orderedPair(userA, userB)
        return matchRepository.findByUser1IdAndUser2Id(user1, user2)
            ?: matchRepository.save(MatchJpaEntity(user1Id = user1, user2Id = user2))
    }

    private fun getOrCreateChatRoom(match: MatchJpaEntity): ChatRoomJpaEntity {
        val matchId = match.idOrThrow()
        chatRoomRepository.findByMatchId(matchId)?.let { return it }

        val room = chatRoomRepository.save(ChatRoomJpaEntity(matchId = matchId))
        val roomId = room.idOrThrow()
        chatRoomMemberRepository.save(ChatRoomMemberJpaEntity(chatRoomId = roomId, userId = match.user1Id))
        chatRoomMemberRepository.save(ChatRoomMemberJpaEntity(chatRoomId = roomId, userId = match.user2Id))
        return room
    }

    private fun findMatch(userA: Long, userB: Long): MatchJpaEntity? {
        val (user1, user2) = orderedPair(userA, userB)
        return matchRepository.findByUser1IdAndUser2Id(user1, user2)
    }

    private fun orderedPair(userA: Long, userB: Long): Pair<Long, Long> =
        if (userA < userB) userA to userB else userB to userA

    private fun UserReactionJpaEntity.idOrThrow(): Long = id ?: error("Persisted reaction is missing id.")

    private fun MatchJpaEntity.idOrThrow(): Long = id ?: error("Persisted match is missing id.")

    private fun ChatRoomJpaEntity.idOrThrow(): Long = id ?: error("Persisted chat room is missing id.")

    private fun UserBlockJpaEntity.toResult(): BlockResult =
        BlockResult(id = id ?: error("Persisted block is missing id."), blockerUserId = blockerUserId, blockedUserId = blockedUserId)

    private companion object {
        const val PASS_EXPIRE_SECONDS: Long = 30L * 24L * 60L * 60L
    }
}

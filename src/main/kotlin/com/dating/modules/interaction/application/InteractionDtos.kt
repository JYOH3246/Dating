package com.dating.modules.interaction.application

import com.dating.modules.interaction.domain.ReactionType
import com.dating.modules.match.domain.MatchStatus
import jakarta.validation.constraints.NotNull
import java.time.Instant

data class ReactionRequest(
    @field:NotNull
    val targetUserId: Long,
    val reactionType: ReactionType,
)

data class ReactionResult(
    val reactionId: Long,
    val reactionType: ReactionType,
    val matched: Boolean,
    val matchId: Long?,
    val chatRoomId: Long?,
)

data class MatchResult(
    val id: Long,
    val counterpartUserId: Long,
    val status: MatchStatus,
    val chatRoomId: Long?,
    val matchedAt: Instant,
)

data class BlockRequest(
    @field:NotNull
    val blockedUserId: Long,
)

data class BlockResult(
    val id: Long,
    val blockerUserId: Long,
    val blockedUserId: Long,
)

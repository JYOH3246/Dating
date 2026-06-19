package com.dating.modules.interaction.infrastructure.jpa

import com.dating.modules.interaction.domain.ReactionStatus
import com.dating.modules.interaction.domain.ReactionType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DiscoveryExposureJpaRepository : JpaRepository<DiscoveryExposureJpaEntity, Long>

interface UserReactionJpaRepository : JpaRepository<UserReactionJpaEntity, Long> {
    fun findByActorUserIdAndTargetUserIdAndStatus(
        actorUserId: Long,
        targetUserId: Long,
        status: ReactionStatus,
    ): UserReactionJpaEntity?

    fun existsByActorUserIdAndTargetUserIdAndReactionTypeAndStatus(
        actorUserId: Long,
        targetUserId: Long,
        reactionType: ReactionType,
        status: ReactionStatus,
    ): Boolean
}

interface UserBlockJpaRepository : JpaRepository<UserBlockJpaEntity, Long> {
    fun findByIdAndBlockerUserId(id: Long, blockerUserId: Long): UserBlockJpaEntity?

    fun findByBlockerUserIdAndBlockedUserId(blockerUserId: Long, blockedUserId: Long): UserBlockJpaEntity?

    fun existsByBlockerUserIdAndBlockedUserId(blockerUserId: Long, blockedUserId: Long): Boolean

    @Query(
        """
        select case when count(b) > 0 then true else false end
        from UserBlockJpaEntity b
        where (b.blockerUserId = :userA and b.blockedUserId = :userB)
           or (b.blockerUserId = :userB and b.blockedUserId = :userA)
        """,
    )
    fun existsBetween(@Param("userA") userA: Long, @Param("userB") userB: Long): Boolean
}

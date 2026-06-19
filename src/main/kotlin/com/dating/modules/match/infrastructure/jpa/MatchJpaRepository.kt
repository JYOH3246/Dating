package com.dating.modules.match.infrastructure.jpa

import com.dating.modules.match.domain.MatchStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MatchJpaRepository : JpaRepository<MatchJpaEntity, Long> {
    fun findByUser1IdAndUser2Id(user1Id: Long, user2Id: Long): MatchJpaEntity?

    @Query(
        """
        select m from MatchJpaEntity m
        where (m.user1Id = :userId or m.user2Id = :userId)
          and m.status = :status
        order by m.matchedAt desc
        """,
    )
    fun findByParticipantAndStatus(
        @Param("userId") userId: Long,
        @Param("status") status: MatchStatus,
    ): List<MatchJpaEntity>
}

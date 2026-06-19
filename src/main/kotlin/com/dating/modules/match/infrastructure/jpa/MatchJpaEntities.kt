package com.dating.modules.match.infrastructure.jpa

import com.dating.modules.match.domain.MatchStatus
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
@Table(name = "matches")
class MatchJpaEntity(
    @Column(name = "user1_id", nullable = false)
    var user1Id: Long,

    @Column(name = "user2_id", nullable = false)
    var user2Id: Long,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "match_status")
    var status: MatchStatus = MatchStatus.ACTIVE,

    @Column(name = "matched_at", nullable = false)
    var matchedAt: Instant = Instant.now(),

    @Column(name = "unmatched_at")
    var unmatchedAt: Instant? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

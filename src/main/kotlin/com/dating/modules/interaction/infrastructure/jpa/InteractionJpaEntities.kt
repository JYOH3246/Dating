package com.dating.modules.interaction.infrastructure.jpa

import com.dating.modules.interaction.domain.ReactionStatus
import com.dating.modules.interaction.domain.ReactionType
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
@Table(name = "discovery_exposures")
class DiscoveryExposureJpaEntity(
    @Column(name = "viewer_user_id", nullable = false)
    var viewerUserId: Long,

    @Column(name = "shown_user_id", nullable = false)
    var shownUserId: Long,

    @Column(name = "exposed_at", nullable = false)
    var exposedAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

@Entity
@Table(name = "user_reactions")
class UserReactionJpaEntity(
    @Column(name = "actor_user_id", nullable = false)
    var actorUserId: Long,

    @Column(name = "target_user_id", nullable = false)
    var targetUserId: Long,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "reaction_type", nullable = false, columnDefinition = "reaction_type")
    var reactionType: ReactionType,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "reaction_status")
    var status: ReactionStatus = ReactionStatus.ACTIVE,

    @Column(name = "expires_at")
    var expiresAt: Instant? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,
) : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

@Entity
@Table(name = "user_blocks")
class UserBlockJpaEntity(
    @Column(name = "blocker_user_id", nullable = false)
    var blockerUserId: Long,

    @Column(name = "blocked_user_id", nullable = false)
    var blockedUserId: Long,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

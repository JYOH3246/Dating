package com.dating.modules.moderation.infrastructure.jpa

import com.dating.modules.moderation.domain.ReportReason
import com.dating.modules.moderation.domain.ReportStatus
import com.dating.modules.moderation.domain.ReportTargetType
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

@Entity
@Table(name = "reports")
class ReportJpaEntity(
    @Column(name = "reporter_user_id", nullable = false)
    var reporterUserId: Long,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "target_type", nullable = false, columnDefinition = "report_target_type")
    var targetType: ReportTargetType,

    @Column(name = "target_id", nullable = false)
    var targetId: Long,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "reason", nullable = false, columnDefinition = "report_reason")
    var reason: ReportReason,

    @Column(name = "reason_detail")
    var reasonDetail: String? = null,

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "report_status")
    var status: ReportStatus = ReportStatus.RECEIVED,
) : BaseJpaEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
}

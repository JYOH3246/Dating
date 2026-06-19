package com.dating.modules.moderation.infrastructure.jpa

import org.springframework.data.jpa.repository.JpaRepository

interface ReportJpaRepository : JpaRepository<ReportJpaEntity, Long>

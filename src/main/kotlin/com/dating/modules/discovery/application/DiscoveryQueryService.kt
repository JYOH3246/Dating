package com.dating.modules.discovery.application

import com.dating.modules.identity.application.UserAccessService
import com.dating.modules.interaction.infrastructure.jpa.DiscoveryExposureJpaEntity
import com.dating.modules.interaction.infrastructure.jpa.DiscoveryExposureJpaRepository
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.Period

@Service
class DiscoveryQueryService(
    private val userAccessService: UserAccessService,
    private val dsl: DSLContext,
    private val exposureRepository: DiscoveryExposureJpaRepository,
) {
    @Transactional
    fun getCardBatch(userId: Long, request: CardBatchRequest): CardBatchResult {
        userAccessService.requireActive(userId)

        val cards = dsl.resultQuery(
            """
            select
              u.id as user_id,
              p.id as profile_id,
              p.nickname as nickname,
              p.birth_date as birth_date,
              p.region_code as region_code,
              p.bio as bio,
              pp.file_key as primary_photo_file_key
            from users u
            join profiles p on p.user_id = u.id
            left join profile_photos pp
              on pp.profile_id = p.id
             and pp.is_primary = true
             and pp.status = 'APPROVED'
             and pp.deleted_at is null
            where u.id <> ?
              and u.status = 'ACTIVE'
              and u.deleted_at is null
              and p.status = 'ACTIVE'
              and p.deleted_at is null
              and exists (
                select 1
                from profile_photos approved
                where approved.profile_id = p.id
                  and approved.status = 'APPROVED'
                  and approved.deleted_at is null
              )
              and not exists (
                select 1
                from user_blocks b
                where (b.blocker_user_id = ? and b.blocked_user_id = u.id)
                   or (b.blocker_user_id = u.id and b.blocked_user_id = ?)
              )
              and not exists (
                select 1
                from user_reactions r
                where r.actor_user_id = ?
                  and r.target_user_id = u.id
                  and r.status = 'ACTIVE'
              )
              and not exists (
                select 1
                from matches m
                where (m.user1_id = ? and m.user2_id = u.id)
                   or (m.user2_id = ? and m.user1_id = u.id)
              )
            order by p.updated_at desc
            limit ?
            """.trimIndent(),
            userId,
            userId,
            userId,
            userId,
            userId,
            userId,
            request.limit,
        ).fetch { record ->
            val birthDate = record.get("birth_date", LocalDate::class.java)
            RecommendationCard(
                userId = record.get("user_id", Long::class.javaObjectType)!!,
                profileId = record.get("profile_id", Long::class.javaObjectType)!!,
                nickname = record.get("nickname", String::class.java),
                age = birthDate?.let { Period.between(it, LocalDate.now()).years },
                regionCode = record.get("region_code", String::class.java),
                bio = record.get("bio", String::class.java),
                primaryPhotoFileKey = record.get("primary_photo_file_key", String::class.java),
            )
        }

        exposureRepository.saveAll(
            cards.map { DiscoveryExposureJpaEntity(viewerUserId = userId, shownUserId = it.userId) },
        )

        return CardBatchResult(items = cards)
    }
}

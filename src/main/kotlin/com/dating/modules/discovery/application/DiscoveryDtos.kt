package com.dating.modules.discovery.application

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class CardBatchRequest(
    @field:Min(1)
    @field:Max(30)
    val limit: Int = 10,
)

data class RecommendationCard(
    val userId: Long,
    val profileId: Long,
    val nickname: String?,
    val age: Int?,
    val regionCode: String?,
    val bio: String?,
    val primaryPhotoFileKey: String?,
)

data class CardBatchResult(
    val items: List<RecommendationCard>,
)

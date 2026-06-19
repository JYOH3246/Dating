package com.dating.modules.discovery.presentation.rest

import com.dating.modules.discovery.application.CardBatchRequest
import com.dating.modules.discovery.application.CardBatchResult
import com.dating.modules.discovery.application.DiscoveryQueryService
import com.dating.platform.security.CurrentUser
import com.dating.shared.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/discovery")
class DiscoveryController(
    private val discoveryQueryService: DiscoveryQueryService,
) {
    @PostMapping("/card-batches")
    fun getCardBatch(
        @CurrentUser userId: Long,
        @Valid @RequestBody request: CardBatchRequest,
    ): ApiResponse<CardBatchResult> {
        return ApiResponse.ok(discoveryQueryService.getCardBatch(userId, request))
    }
}

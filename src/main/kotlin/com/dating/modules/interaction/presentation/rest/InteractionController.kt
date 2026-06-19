package com.dating.modules.interaction.presentation.rest

import com.dating.modules.interaction.application.BlockRequest
import com.dating.modules.interaction.application.BlockResult
import com.dating.modules.interaction.application.InteractionService
import com.dating.modules.interaction.application.MatchResult
import com.dating.modules.interaction.application.ReactionRequest
import com.dating.modules.interaction.application.ReactionResult
import com.dating.platform.security.CurrentUser
import com.dating.shared.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping
class InteractionController(
    private val interactionService: InteractionService,
) {
    @PostMapping("/reactions")
    fun react(
        @CurrentUser userId: Long,
        @Valid @RequestBody request: ReactionRequest,
    ): ApiResponse<ReactionResult> {
        return ApiResponse.ok(interactionService.react(userId, request))
    }

    @GetMapping("/matches")
    fun getMatches(@CurrentUser userId: Long): ApiResponse<List<MatchResult>> {
        return ApiResponse.ok(interactionService.getMatches(userId))
    }

    @DeleteMapping("/matches/{id}")
    fun unmatch(
        @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ApiResponse<MatchResult> {
        return ApiResponse.ok(interactionService.unmatch(userId, id))
    }

    @PostMapping("/blocks")
    fun block(
        @CurrentUser userId: Long,
        @Valid @RequestBody request: BlockRequest,
    ): ApiResponse<BlockResult> {
        return ApiResponse.ok(interactionService.block(userId, request))
    }

    @DeleteMapping("/blocks/{id}")
    fun unblock(
        @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ApiResponse<Unit> {
        interactionService.unblock(userId, id)
        return ApiResponse.empty()
    }
}

package com.dating.modules.chat.presentation.rest

import com.dating.modules.chat.application.ChatMessageResult
import com.dating.modules.chat.application.ChatRoomResult
import com.dating.modules.chat.application.ChatService
import com.dating.modules.chat.application.SendMessageRequest
import com.dating.platform.security.CurrentUser
import com.dating.shared.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/chat")
class ChatController(
    private val chatService: ChatService,
) {
    @GetMapping("/rooms")
    fun getRooms(@CurrentUser userId: Long): ApiResponse<List<ChatRoomResult>> {
        return ApiResponse.ok(chatService.getRooms(userId))
    }

    @GetMapping("/rooms/{id}/messages")
    fun getMessages(
        @CurrentUser userId: Long,
        @PathVariable id: Long,
    ): ApiResponse<List<ChatMessageResult>> {
        return ApiResponse.ok(chatService.getMessages(userId, id))
    }

    @PostMapping("/rooms/{id}/messages")
    fun sendMessage(
        @CurrentUser userId: Long,
        @PathVariable id: Long,
        @Valid @RequestBody request: SendMessageRequest,
    ): ApiResponse<ChatMessageResult> {
        return ApiResponse.ok(chatService.sendMessage(userId, id, request))
    }
}

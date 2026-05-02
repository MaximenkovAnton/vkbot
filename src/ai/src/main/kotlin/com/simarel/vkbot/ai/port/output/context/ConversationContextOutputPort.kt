package com.simarel.vkbot.ai.port.output.context

import com.simarel.vkbot.ai.port.output.ai.ChatMessage
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface ConversationContextOutputPort :
    OutputPort<
            ConversationContextRequest,
            ConversationContextResponse,
            >

data class ConversationContextRequest(
    val message: Message,
) : OutputPortRequest

data class ConversationContextResponse(
    val currentMessage: Message,
    val chatHistory: List<ChatMessage>,
    val userProfiles: Map<FromId, VkUserProfile>,
    val groupProfiles: Map<FromId, VkGroupProfile>,
) : OutputPortResponse

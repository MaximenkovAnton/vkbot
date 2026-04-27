package com.simarel.vkbot.ai.port.output.ai

import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.Date
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import com.simarel.vkbot.share.port.output.OutputPort

interface GenerateAnswerOutputPort :
    OutputPort<
            GenerateAnswerOutputPortRequest,
            GenerateAnswerOutputPortResponse,
            >

data class GenerateAnswerOutputPortRequest(
    val currentMessage: Message,
    val chatHistory: List<ChatMessage>,
    val userProfiles: Map<FromId, VkUserProfile>,
    val groupProfiles: Map<FromId, VkGroupProfile>,
) : OutputPortRequest

data class ChatMessage(
    val id: ConversationMessageId,
    val fromId: FromId,
    val text: MessageText,
    val date: Date,
    val forwardedMessages: List<Message> = emptyList(),
)

@JvmInline
value class GenerateAnswerOutputPortResponse(val answer: String) : OutputPortResponse

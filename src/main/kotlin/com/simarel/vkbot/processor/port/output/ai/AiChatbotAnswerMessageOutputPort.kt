package com.simarel.vkbot.processor.port.output.ai

import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.vo.MessageText

interface AiChatbotAnswerMessageOutputPort :
    AiOutputPort<
        AiChatbotAnswerMessageOutputPortRequest,
        AiChatbotAnswerMessageOutputPortResponse,
        >

@JvmInline
value class AiChatbotAnswerMessageOutputPortRequest(val message: Message) : AiOutputPortRequest

@JvmInline
value class AiChatbotAnswerMessageOutputPortResponse(val responseMessage: MessageText) : AiOutputPortResponse

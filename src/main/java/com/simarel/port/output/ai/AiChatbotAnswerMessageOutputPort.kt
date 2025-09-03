package com.simarel.port.output.ai

import com.simarel.domain.model.Message
import com.simarel.domain.vo.MessageText

interface AiChatbotAnswerMessageOutputPort: AiPort<AiChatbotAnswerMessageOutputRequest, AiChatbotAnswerMessageOutputResponse>

@JvmInline
value class AiChatbotAnswerMessageOutputRequest(val message: Message): AiOutputRequest

@JvmInline
value class AiChatbotAnswerMessageOutputResponse(val responseMessage: MessageText): AiOutputResponse
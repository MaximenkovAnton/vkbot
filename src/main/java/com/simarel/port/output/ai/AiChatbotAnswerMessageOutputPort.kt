package com.simarel.port.output.ai

import com.simarel.domain.model.Message
import com.simarel.domain.vo.MessageText

interface AiChatbotAnswerMessageOutputPort: AiPort<AiChatbotAnswerMessageOutputPortRequest, AiChatbotAnswerMessageOutputPortResponse>

@JvmInline
value class AiChatbotAnswerMessageOutputPortRequest(val message: Message): AiOutputPortRequest

@JvmInline
value class AiChatbotAnswerMessageOutputPortResponse(val responseMessage: MessageText): AiOutputPortResponse
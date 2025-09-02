package com.simarel.port.output.ai

import com.simarel.domain.model.Message

interface AiChatbotAnswerMessageOutputPort: AiPort<AiChatbotAnswerMessageOutputRequest, AiChatbotAnswerMessageOutputResponse>

@JvmInline
value class AiChatbotAnswerMessageOutputRequest(val message: Message): AiOutputRequest

@JvmInline
value class AiChatbotAnswerMessageOutputResponse(val value: String): AiOutputResponse
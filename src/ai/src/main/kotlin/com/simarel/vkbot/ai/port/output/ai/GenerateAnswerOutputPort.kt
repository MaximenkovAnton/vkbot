package com.simarel.vkbot.ai.port.output.ai

import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse
import com.simarel.vkbot.share.port.output.OutputPort

interface GenerateAnswerOutputPort :
    OutputPort<
            GenerateAnswerOutputPortRequest,
            GenerateAnswerOutputPortResponse,
            >

data class GenerateAnswerOutputPortRequest(
    val memoryId: String,
    val userMessage: String,
    val messageContext: String,
) : OutputPortRequest

@JvmInline
value class GenerateAnswerOutputPortResponse(val answer: String) : OutputPortResponse

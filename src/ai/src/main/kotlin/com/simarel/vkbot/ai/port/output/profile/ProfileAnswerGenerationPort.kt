package com.simarel.vkbot.ai.port.output.profile

import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

fun interface ProfileAnswerGenerationPort : OutputPort<ProfileAnswerGenerationRequest, ProfileAnswerGenerationResponse>

data class ProfileAnswerGenerationRequest(
    val question: String,
    val data: String,
) : OutputPortRequest

data class ProfileAnswerGenerationResponse(
    val text: String,
) : OutputPortResponse

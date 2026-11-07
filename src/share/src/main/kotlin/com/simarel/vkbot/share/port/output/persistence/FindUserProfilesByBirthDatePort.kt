package com.simarel.vkbot.share.port.output.persistence

import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindUserProfilesByBirthDatePort :
    OutputPort<
        FindUserProfilesByBirthDatePort.Request,
        FindUserProfilesByBirthDatePort.Response,
        > {
    data class Request(val day: Int, val month: Int) : OutputPortRequest
    data class Response(val profiles: List<VkUserProfile>) : OutputPortResponse
}

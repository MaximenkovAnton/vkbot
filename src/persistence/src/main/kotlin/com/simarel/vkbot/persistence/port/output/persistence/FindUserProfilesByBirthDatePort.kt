package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface FindUserProfilesByBirthDatePort :
    OutputPort<
        FindUserProfilesByBirthDatePort.FindUserProfilesByBirthDateRequest,
        FindUserProfilesByBirthDatePort.FindUserProfilesByBirthDateResponse,
        > {
    data class FindUserProfilesByBirthDateRequest(val day: Int, val month: Int) : OutputPortRequest
    data class FindUserProfilesByBirthDateResponse(val profiles: List<VkUserProfile>) : OutputPortResponse
}

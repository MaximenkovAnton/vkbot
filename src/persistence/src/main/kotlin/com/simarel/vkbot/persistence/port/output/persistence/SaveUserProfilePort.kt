package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface SaveUserProfilePort : OutputPort<SaveUserProfilePort.SaveUserProfileRequest, SaveUserProfilePort.SaveUserProfileResponse> {
    data class SaveUserProfileRequest(val profile: VkUserProfile) : OutputPortRequest
    class SaveUserProfileResponse : OutputPortResponse
}

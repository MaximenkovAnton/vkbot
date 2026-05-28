package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface SaveGroupProfilePort : OutputPort<SaveGroupProfilePort.SaveGroupProfileRequest, SaveGroupProfilePort.SaveGroupProfileResponse> {
    data class SaveGroupProfileRequest(val profile: VkGroupProfile) : OutputPortRequest
    class SaveGroupProfileResponse : OutputPortResponse
}

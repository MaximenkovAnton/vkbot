package com.simarel.vkbot.persistence.port.output.persistence

import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.OutputPortRequest
import com.simarel.vkbot.share.port.output.OutputPortResponse

interface SaveMessagePort : OutputPort<SaveMessagePort.SaveMessageRequest, SaveMessagePort.SaveMessageResponse> {
    data class SaveMessageRequest(val message: MessageEntity) : OutputPortRequest
    class SaveMessageResponse : OutputPortResponse
}

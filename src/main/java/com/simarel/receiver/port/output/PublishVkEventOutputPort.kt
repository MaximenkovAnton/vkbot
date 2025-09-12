package com.simarel.receiver.port.output

import com.simarel.receiver.domain.vo.VkEvent
import com.simarel.share.port.output.OutputPort
import com.simarel.share.port.output.OutputPortRequest
import com.simarel.share.port.output.OutputPortResponse

interface PublishVkEventOutputPort: OutputPort<PublishVkEventOutputPortRequest, PublishVkEventOutputPortResponse>

@JvmInline
value class PublishVkEventOutputPortRequest(val vkEvent: VkEvent): OutputPortRequest

class PublishVkEventOutputPortResponse(): OutputPortResponse
package com.simarel.vkbot.share.port.output

import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.vo.Payload

interface PublishEventOutputPort: OutputPort<PublishEventOutputPortRequest, PublishEventOutputPortResponse>

class PublishEventOutputPortRequest(val event: Event, val payload: Payload): OutputPortRequest

class PublishEventOutputPortResponse(): OutputPortResponse
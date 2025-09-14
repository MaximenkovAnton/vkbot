package com.simarel.vk.share.port.output

import com.simarel.vk.share.domain.Event
import com.simarel.vk.share.domain.vo.Payload

interface PublishEventOutputPort: OutputPort<PublishEventOutputPortRequest, PublishEventOutputPortResponse>

class PublishEventOutputPortRequest(val event: Event, val payload: Payload): OutputPortRequest

class PublishEventOutputPortResponse(): OutputPortResponse
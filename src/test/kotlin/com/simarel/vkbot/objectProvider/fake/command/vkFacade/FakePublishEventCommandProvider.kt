package com.simarel.vkbot.objectProvider.fake.command.vkFacade

import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.vo.Payload
import com.simarel.vkbot.vkFacade.command.publishEvent.PublishEventRequest

object FakePublishEventCommandProvider {
    fun createRequest(event: Event? = null, payload: Payload? = null) = PublishEventRequest(
        event ?: Event.MESSAGE_RECEIVED,
        payload ?: Payload("test payload"),
    )
}

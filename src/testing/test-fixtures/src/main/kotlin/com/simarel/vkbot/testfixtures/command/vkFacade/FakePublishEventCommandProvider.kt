package com.simarel.vkbot.testfixtures.command.vkFacade

import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.vo.Payload
import com.simarel.vkbot.share.command.publishEvent.PublishEventRequest

object FakePublishEventCommandProvider {
    fun createRequest(
        event: Event = Event.MESSAGE_RECEIVED,
        payload: Payload = Payload("test payload"),
    ) = PublishEventRequest(
        event = event,
        payload = payload,
    )
}

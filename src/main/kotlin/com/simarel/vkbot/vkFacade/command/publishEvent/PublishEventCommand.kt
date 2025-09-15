package com.simarel.vkbot.vkFacade.command.publishEvent

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.processor.domain.vo.MessageText
import com.simarel.vkbot.processor.domain.vo.PeerId
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.vo.Payload

interface PublishEventCommand: Command<PublishEventRequest, PublishEventResponse>

class PublishEventRequest(val event: Event, val payload: Payload): CommandRequest

class PublishEventResponse: CommandResponse

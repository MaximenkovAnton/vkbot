package com.simarel.vkbot.receiver.command.sendVkEvent

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.model.Message

interface PublishVkEventCommand : Command<PublishVkEventCommandRequest, PublishVkEventCommandResponse>

data class PublishVkEventCommandRequest(val message: Message) : CommandRequest

class PublishVkEventCommandResponse : CommandResponse

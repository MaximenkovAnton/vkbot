package com.simarel.receiver.command.sendVkEvent

import com.simarel.receiver.domain.vo.VkEvent
import com.simarel.share.command.Command
import com.simarel.share.command.CommandRequest
import com.simarel.share.command.CommandResponse

interface SendVkEventCommand: Command<PublishVkEventCommandRequest, PublishVkEventCommandResponse>

@JvmInline
value class PublishVkEventCommandRequest(val vkEvent: VkEvent): CommandRequest

class PublishVkEventCommandResponse(): CommandResponse
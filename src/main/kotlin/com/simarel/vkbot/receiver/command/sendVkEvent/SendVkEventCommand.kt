package com.simarel.vkbot.receiver.command.sendVkEvent

import com.simarel.vkbot.receiver.domain.vo.VkEvent
import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse

interface SendVkEventCommand: Command<PublishVkEventCommandRequest, PublishVkEventCommandResponse>

@JvmInline
value class PublishVkEventCommandRequest(val vkEvent: VkEvent): CommandRequest

class PublishVkEventCommandResponse(): CommandResponse
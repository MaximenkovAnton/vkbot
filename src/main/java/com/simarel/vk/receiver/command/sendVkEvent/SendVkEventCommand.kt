package com.simarel.vk.receiver.command.sendVkEvent

import com.simarel.vk.receiver.domain.vo.VkEvent
import com.simarel.vk.share.command.Command
import com.simarel.vk.share.command.CommandRequest
import com.simarel.vk.share.command.CommandResponse

interface SendVkEventCommand: Command<PublishVkEventCommandRequest, PublishVkEventCommandResponse>

@JvmInline
value class PublishVkEventCommandRequest(val vkEvent: VkEvent): CommandRequest

class PublishVkEventCommandResponse(): CommandResponse
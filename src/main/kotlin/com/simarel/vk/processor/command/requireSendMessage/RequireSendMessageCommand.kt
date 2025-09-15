package com.simarel.vk.processor.command.requireSendMessage

import com.simarel.vk.processor.domain.model.Message
import com.simarel.vk.share.command.Command
import com.simarel.vk.share.command.CommandRequest
import com.simarel.vk.share.command.CommandResponse
import com.simarel.vk.processor.domain.vo.MessageText
import com.simarel.vk.processor.domain.vo.PeerId

interface RequireSendMessageCommand: Command<RequireSendMessageCommandRequest, RequireSendMessageCommandResponse>

@JvmInline
value class RequireSendMessageCommandRequest(val message: Message): CommandRequest

class RequireSendMessageCommandResponse: CommandResponse

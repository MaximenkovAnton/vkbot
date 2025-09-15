package com.simarel.vkbot.processor.command.requireSendMessage

import com.simarel.vkbot.processor.domain.model.Message
import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse

interface RequireSendMessageCommand: Command<RequireSendMessageCommandRequest, RequireSendMessageCommandResponse>

@JvmInline
value class RequireSendMessageCommandRequest(val message: Message): CommandRequest

class RequireSendMessageCommandResponse: CommandResponse

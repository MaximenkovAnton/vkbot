package com.simarel.vkbot.processor.command.answer

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.vo.MessageText

interface MessageAnswerTextGenerateCommand:
    Command<MessageAnswerTextGenerateCommandRequest, MessageAnswerTextGenerateCommandResponse>

@JvmInline
value class MessageAnswerTextGenerateCommandRequest(val message: Message): CommandRequest

@JvmInline
value class MessageAnswerTextGenerateCommandResponse(val messageText: MessageText): CommandResponse

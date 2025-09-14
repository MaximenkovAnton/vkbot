package com.simarel.vk.processor.command.answer

import com.simarel.vk.share.command.Command
import com.simarel.vk.share.command.CommandRequest
import com.simarel.vk.share.command.CommandResponse
import com.simarel.vk.processor.domain.model.Message
import com.simarel.vk.processor.domain.vo.MessageText

interface MessageAnswerTextGenerateCommand:
    Command<MessageAnswerTextGenerateCommandRequest, MessageAnswerTextGenerateCommandResponse>

@JvmInline
value class MessageAnswerTextGenerateCommandRequest(val message: Message): CommandRequest

@JvmInline
value class MessageAnswerTextGenerateCommandResponse(val messageText: MessageText): CommandResponse

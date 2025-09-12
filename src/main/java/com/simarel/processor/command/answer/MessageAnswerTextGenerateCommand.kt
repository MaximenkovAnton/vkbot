package com.simarel.processor.command.answer

import com.simarel.share.command.Command
import com.simarel.share.command.CommandRequest
import com.simarel.share.command.CommandResponse
import com.simarel.processor.domain.model.Message
import com.simarel.processor.domain.vo.MessageText

interface MessageAnswerTextGenerateCommand:
    Command<MessageAnswerTextGenerateCommandRequest, MessageAnswerTextGenerateCommandResponse>

@JvmInline
value class MessageAnswerTextGenerateCommandRequest(val message: Message): CommandRequest

@JvmInline
value class MessageAnswerTextGenerateCommandResponse(val messageText: MessageText): CommandResponse

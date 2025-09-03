package com.simarel.command.answer

import com.simarel.command.Command
import com.simarel.command.CommandRequest
import com.simarel.command.CommandResponse
import com.simarel.domain.model.Message
import com.simarel.domain.vo.MessageText

interface MessageAnswerTextGenerateCommand:
    Command<MessageAnswerTextGenerateCommandRequest, MessageAnswerTextGenerateCommandResponse>

@JvmInline
value class MessageAnswerTextGenerateCommandRequest(val message: Message): CommandRequest

@JvmInline
value class MessageAnswerTextGenerateCommandResponse(val messageText: MessageText): CommandResponse

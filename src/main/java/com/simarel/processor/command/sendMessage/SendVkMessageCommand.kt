package com.simarel.processor.command.sendMessage

import com.simarel.share.command.Command
import com.simarel.share.command.CommandRequest
import com.simarel.share.command.CommandResponse
import com.simarel.processor.domain.vo.MessageText
import com.simarel.processor.domain.vo.PeerId

interface SendVkMessageCommand: Command<SendVkMessageCommandRequest, SendVkMessageCommandResponse>

class SendVkMessageCommandRequest(val peerId: PeerId, val message: MessageText): CommandRequest

class SendVkMessageCommandResponse: CommandResponse

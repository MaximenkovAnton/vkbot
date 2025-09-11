package com.simarel.command.sendMessage

import com.simarel.command.Command
import com.simarel.command.CommandRequest
import com.simarel.command.CommandResponse
import com.simarel.domain.vo.MessageText
import com.simarel.domain.vo.PeerId

interface SendVkMessageCommand: Command<SendVkMessageCommandRequest, SendVkMessageCommandResponse>

class SendVkMessageCommandRequest(val peerId: PeerId, val message: MessageText): CommandRequest

class SendVkMessageCommandResponse: CommandResponse

package com.simarel.vk.processor.command.sendVkMessage

import com.simarel.vk.share.command.Command
import com.simarel.vk.share.command.CommandRequest
import com.simarel.vk.share.command.CommandResponse
import com.simarel.vk.processor.domain.vo.MessageText
import com.simarel.vk.processor.domain.vo.PeerId

interface SendVkMessageCommand: Command<SendVkMessageCommandRequest, SendVkMessageCommandResponse>

class SendVkMessageCommandRequest(val peerId: PeerId, val message: MessageText): CommandRequest

class SendVkMessageCommandResponse: CommandResponse

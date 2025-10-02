package com.simarel.vkbot.vkFacade.command.sendVkMessage

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId

interface SendVkMessageCommand : Command<SendVkMessageCommandRequest, SendVkMessageCommandResponse>

class SendVkMessageCommandRequest(val peerId: PeerId, val message: MessageText) : CommandRequest

class SendVkMessageCommandResponse : CommandResponse

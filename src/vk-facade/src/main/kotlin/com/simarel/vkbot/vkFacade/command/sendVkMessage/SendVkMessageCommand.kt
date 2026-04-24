package com.simarel.vkbot.vkFacade.command.sendVkMessage

import com.simarel.vkbot.share.command.Command
import com.simarel.vkbot.share.command.CommandRequest
import com.simarel.vkbot.share.command.CommandResponse
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.vkFacade.domain.ForwardedMessages

interface SendVkMessageCommand : Command<SendVkMessageCommandRequest, SendVkMessageCommandResponse>

class SendVkMessageCommandRequest(val peerId: PeerId, val message: MessageText, val forwardedMessages: ForwardedMessages?, val rand: Int = 0) : CommandRequest

class SendVkMessageCommandResponse : CommandResponse

package com.simarel.vkbot.share.domain.model

import com.simarel.vkbot.share.domain.vo.MessageText

data class ResponseMessage(
    val messageText: MessageText,
    val responseTo: Message
)

package com.simarel.vkbot.share.domain.model

import com.simarel.vkbot.share.domain.vo.FromId

data class ChatAuthor(
    val fromId: FromId,
    val messageCount: Long,
)

package com.simarel.vkbot.ai.domain.knowledge

import com.simarel.vkbot.share.domain.model.StoredMessage

object UserKnowledgeBatchFormatter {

    fun format(messages: List<StoredMessage>, authorNames: Map<Long, String>): List<String> = messages.map { message ->
        val fromId = message.fromId
        val authorName = authorNames[fromId] ?: "Пользователь $fromId"
        "[msg=${message.conversationMessageId}|from=$fromId|$authorName]: ${message.messageText.orEmpty()}"
    }
}

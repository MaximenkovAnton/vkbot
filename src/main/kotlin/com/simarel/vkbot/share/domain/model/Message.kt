package com.simarel.vkbot.share.domain.model

import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.Date
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.GroupId
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import java.time.OffsetDateTime

data class Message(
    val date: Date,
    val fromId: FromId,
    val groupId: GroupId,
    val peerId: PeerId,
    val conversationMessageId: ConversationMessageId,
    val messageText: MessageText,
    val forwardedMessages: List<Message> = emptyList(),
) {
    companion object {
        fun of(
            date: OffsetDateTime?,
            fromId: Long?,
            groupId: Long?,
            peerId: Long?,
            conversationMessageId: Long?,
            messageText: String?,
            forwardedMessages: List<Message>? = null,
        ): Message = Message(
            date = Date.of(date),
            fromId = FromId.of(fromId),
            groupId = GroupId.of(groupId),
            peerId = PeerId.of(peerId),
            conversationMessageId = ConversationMessageId.of(conversationMessageId),
            messageText = MessageText.of(messageText),
            forwardedMessages = forwardedMessages ?: emptyList(),
        )
    }

    fun answer(text: MessageText): Message = this.copy(messageText = text)

    fun isRequireAnswer(): Boolean {
        if (!fromId.isGroupChat()) {
            return true // direct message to bot
        }
        if (messageText.startsWith("!") || messageText.startsWith("\\")) {
            return false // command for controlling bot
        }
        if (!fromId.isHuman()) {
            return false // not a human
        }
        if (messageText.contains("@simarel")) { // todo: get rid of hardcoded name
            return true // direct call to bot
        }
        // Check if any forwarded message requires answer
        if (forwardedMessages.any { it.isRequireAnswer() }) {
            return true
        }
        // todo: after adding forwarded messages, check if the message is a reply to a message from the bot
        return false
    }
}

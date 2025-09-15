package com.simarel.vk.processor.domain.model

import com.simarel.vk.processor.domain.vo.ConversationMessageId
import com.simarel.vk.processor.domain.vo.Date
import com.simarel.vk.processor.domain.vo.FromId
import com.simarel.vk.processor.domain.vo.GroupId
import com.simarel.vk.processor.domain.vo.MessageText
import com.simarel.vk.processor.domain.vo.PeerId
import java.time.OffsetDateTime

data class Message(
    val date: Date,
    val fromId: FromId,
    val groupId: GroupId,
    val peerId: PeerId,
    val conversationMessageId: ConversationMessageId,
    val messageText: MessageText,
) {
    companion object {
        fun of(
            date: OffsetDateTime,
            fromId: Long,
            groupId: Long,
            peerId: Long,
            conversationMessageId: Long,
            messageText: String,
        ): Message = Message(
            date = Date.of(date),
            fromId = FromId.of(fromId),
            groupId = GroupId.of(groupId),
            peerId = PeerId.of(peerId),
            conversationMessageId = ConversationMessageId.of(conversationMessageId),
            messageText = MessageText.of(messageText),
        )
    }
}

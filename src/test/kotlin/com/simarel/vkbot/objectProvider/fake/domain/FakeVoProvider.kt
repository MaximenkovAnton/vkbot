package com.simarel.vkbot.objectProvider.fake.domain

import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.Date
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.GroupId
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import java.time.OffsetDateTime
import kotlin.random.Random
import kotlin.random.nextLong
import kotlin.random.nextUInt

object FakeVoProvider {
    fun createDate(value: OffsetDateTime? = null) = Date(value ?: OffsetDateTime.now())
    fun createFromId(value: Long? = null) = FromId.Companion.of(value ?: (Random.nextUInt().toLong() % 2_000_000))
    fun createGroupId(value: Long? = null) = GroupId.Companion.of(value ?: Random.nextLong())
    fun createPeerId(value: Long? = null) = PeerId.Companion.of(value ?: Random.nextLong())
    fun createConversationMessageId(value: Long? = null) = ConversationMessageId.Companion.of(
        value ?: Random.nextLong(),
    )
    fun createMessageText(value: String? = null) = MessageText.Companion.of(value ?: "Тестовое сообщение для @simarel")

    fun createMessage(
        date: Date? = null,
        fromId: FromId? = null,
        groupId: GroupId? = null,
        peerId: PeerId? = null,
        conversationMessageId: ConversationMessageId? = null,
        messageText: MessageText? = null,
        forwardedMessages: List<Message>? = null,
    ) = Message(
        date = date ?: createDate(),
        fromId = fromId ?: createFromId(),
        groupId = groupId ?: createGroupId(),
        peerId = peerId ?: createPeerId(),
        conversationMessageId = conversationMessageId ?: createConversationMessageId(),
        messageText = messageText ?: createMessageText(),
        forwardedMessages = forwardedMessages ?: emptyList(),
    )

    fun createMessageWithForwarded(): Message {
        val forwardedMessage1 = createMessage(
            fromId = createFromId(11111),
            messageText = createMessageText("forwarded message 1"),
        )

        val forwardedMessage2 = createMessage(
            fromId = createFromId(22222),
            messageText = createMessageText("forwarded message 2"),
        )

        return createMessage(
            fromId = createFromId(12345),
            peerId = createPeerId(67890),
            messageText = createMessageText("actual message"),
            forwardedMessages = listOf(forwardedMessage1, forwardedMessage2),
        )
    }
}

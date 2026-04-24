package com.simarel.vkbot.testfixtures.domain

import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import com.simarel.vkbot.share.domain.vo.Date
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.GroupId
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.vkFacade.domain.ForwardedMessages
import java.time.OffsetDateTime
import kotlin.random.Random

object FakeVoProvider {
    fun createDate(value: OffsetDateTime? = null) = Date(value ?: OffsetDateTime.now())

    fun createHumanFromId() = FromId.of(Random.nextLong(1, 1_999_999_999))
    fun createGroupFromId() = FromId.of(-Random.nextLong(1, 999_999_999))
    fun createGroupChatFromId() = FromId.of(Random.nextLong(2_000_000_001, Long.MAX_VALUE))

    fun createGroupId(value: Long? = null) = GroupId.of(value ?: Random.nextLong())
    fun createPeerId(value: Long? = null) = PeerId.of(value ?: Random.nextLong())
    fun createConversationMessageId(value: Long? = null) = ConversationMessageId.of(
        value ?: Random.nextLong(),
    )

    fun createMessageText(value: String? = null) = MessageText.of(value ?: "Тестовое сообщение для @simarel")

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
        fromId = fromId ?: createHumanFromId(),
        groupId = groupId ?: createGroupId(),
        peerId = peerId ?: createPeerId(),
        conversationMessageId = conversationMessageId ?: createConversationMessageId(),
        messageText = messageText ?: createMessageText(),
        forwardedMessages = forwardedMessages ?: emptyList(),
    )

    fun createForwardedMessage(message: Message? = null) = ForwardedMessages(message ?: createMessage())

    fun createMessageWithForwarded(): Message {
        val forwardedMessage1 = createMessage(
            messageText = createMessageText("forwarded message 1"),
        )

        val forwardedMessage2 = createMessage(
            messageText = createMessageText("forwarded message 2"),
        )

        return createMessage(
            messageText = createMessageText("actual message"),
            forwardedMessages = listOf(forwardedMessage1, forwardedMessage2),
        )
    }
}

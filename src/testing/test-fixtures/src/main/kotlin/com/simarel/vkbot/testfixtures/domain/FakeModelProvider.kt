package com.simarel.vkbot.testfixtures.domain

import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.ResponseMessage
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.domain.model.SummaryStatus
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.MessageText
import java.time.OffsetDateTime
import java.util.UUID

private val FIXED_DATE: OffsetDateTime = OffsetDateTime.parse("2026-01-01T00:00:00Z")

object FakeModelProvider {

    fun createStoredMessage(
        id: UUID = UUID.randomUUID(),
        peerId: Long = 10,
        conversationMessageId: Long = 20,
        fromId: Long = 30,
        messageText: String? = "text",
        date: OffsetDateTime? = FIXED_DATE,
        forwardedMessages: String? = null,
    ) = StoredMessage(
        id = id,
        peerId = peerId,
        conversationMessageId = conversationMessageId,
        fromId = fromId,
        messageText = messageText,
        date = date,
        forwardedMessages = forwardedMessages,
    )

    @Suppress("LongParameterList")
    fun createSummary(
        id: UUID = UUID.randomUUID(),
        peerId: Long = 1,
        firstMessageId: Long? = 2,
        lastMessageId: Long? = 3,
        shortSummary: String? = "short summary",
        fullSummary: String? = "full summary",
        status: SummaryStatus = SummaryStatus.COMPLETED,
        createdAt: OffsetDateTime = FIXED_DATE,
        updatedAt: OffsetDateTime = FIXED_DATE,
    ) = Summary(
        id = id,
        peerId = peerId,
        firstMessageId = firstMessageId,
        lastMessageId = lastMessageId,
        shortSummary = shortSummary,
        fullSummary = fullSummary,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    fun createVkUserProfile(
        id: Long? = null,
        firstName: String? = "Ivan",
        lastName: String? = "Petrov",
        screenName: String? = "ivan_petrov",
        birthDate: String? = "01.01.1990",
        city: String? = "Moscow",
    ) = VkUserProfile(
        id = FakeVoProvider.createFromId(id),
        firstName = firstName,
        lastName = lastName,
        screenName = screenName,
        birthDate = birthDate,
        city = city,
        lastUpdated = FIXED_DATE,
    )

    fun createVkGroupProfile(
        id: Long? = null,
        name: String? = "Test Group",
        screenName: String? = "testgroup",
    ) = VkGroupProfile(
        id = FakeVoProvider.createFromId(id),
        name = name,
        screenName = screenName,
        lastUpdated = FIXED_DATE,
    )

    fun createResponseMessage(
        messageText: MessageText? = null,
        responseTo: Message? = null,
    ) = ResponseMessage(
        messageText = messageText ?: FakeVoProvider.createMessageText(),
        responseTo = responseTo ?: FakeVoProvider.createMessage(),
    )
}

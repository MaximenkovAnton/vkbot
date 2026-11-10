package com.simarel.vkbot.testfixtures.domain

import com.simarel.vkbot.share.domain.model.ChatAuthor
import com.simarel.vkbot.share.domain.model.ChatStatistics
import com.simarel.vkbot.share.domain.model.DayOfWeekActivity
import com.simarel.vkbot.share.domain.model.HourActivity
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.ProfileHistoryEntry
import com.simarel.vkbot.share.domain.model.ResponseMessage
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.domain.model.SummaryStatus
import com.simarel.vkbot.share.domain.model.UserStatistics
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.vo.MessageText
import java.time.OffsetDateTime
import java.util.UUID

private val FIXED_DATE: OffsetDateTime = OffsetDateTime.parse("2026-01-01T00:00:00Z")

@Suppress("TooManyFunctions")
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

    @Suppress("LongParameterList")
    fun createVkUserProfile(
        id: Long? = null,
        firstName: String? = "Ivan",
        lastName: String? = "Petrov",
        screenName: String? = "ivan_petrov",
        birthDate: String? = "01.01.1990",
        city: String? = "Moscow",
        lastUpdated: OffsetDateTime = FIXED_DATE,
        details: Map<String, Any?> = emptyMap(),
    ) = VkUserProfile(
        id = FakeVoProvider.createFromId(id),
        firstName = firstName,
        lastName = lastName,
        screenName = screenName,
        birthDate = birthDate,
        city = city,
        lastUpdated = lastUpdated,
        details = details,
    )

    fun createVkGroupProfile(
        id: Long? = null,
        name: String? = "Test Group",
        screenName: String? = "testgroup",
        lastUpdated: OffsetDateTime = FIXED_DATE,
    ) = VkGroupProfile(
        id = FakeVoProvider.createFromId(id),
        name = name,
        screenName = screenName,
        lastUpdated = lastUpdated,
    )

    fun createResponseMessage(
        messageText: MessageText? = null,
        responseTo: Message? = null,
    ) = ResponseMessage(
        messageText = messageText ?: FakeVoProvider.createMessageText(),
        responseTo = responseTo ?: FakeVoProvider.createMessage(),
    )

    fun createChatAuthor(
        fromId: Long? = null,
        messageCount: Long = 1,
    ) = ChatAuthor(
        fromId = FakeVoProvider.createFromId(fromId),
        messageCount = messageCount,
    )

    @Suppress("LongParameterList")
    fun createUserStatistics(
        userId: Long = 1,
        messageCount: Long = 0,
        totalLength: Long = 0,
        averageLength: Double = 0.0,
        firstMessageAt: OffsetDateTime? = null,
        lastMessageAt: OffsetDateTime? = null,
        chats: List<ChatStatistics> = emptyList(),
        activityByHour: List<HourActivity> = emptyList(),
        activityByDayOfWeek: List<DayOfWeekActivity> = emptyList(),
    ) = UserStatistics(
        userId = userId,
        messageCount = messageCount,
        totalLength = totalLength,
        averageLength = averageLength,
        firstMessageAt = firstMessageAt,
        lastMessageAt = lastMessageAt,
        chats = chats,
        activityByHour = activityByHour,
        activityByDayOfWeek = activityByDayOfWeek,
    )

    fun createChatStatistics(
        peerId: Long = 1,
        messageCount: Long = 0,
        totalLength: Long = 0,
        averageLength: Double = 0.0,
        lastMessageAt: OffsetDateTime? = null,
    ) = ChatStatistics(
        peerId = peerId,
        messageCount = messageCount,
        totalLength = totalLength,
        averageLength = averageLength,
        lastMessageAt = lastMessageAt,
    )

    fun createHourActivity(
        hour: Int = 10,
        messageCount: Long = 1,
    ) = HourActivity(hour = hour, messageCount = messageCount)

    fun createDayOfWeekActivity(
        dayOfWeek: Int = 1,
        messageCount: Long = 1,
    ) = DayOfWeekActivity(dayOfWeek = dayOfWeek, messageCount = messageCount)

    fun createProfileHistoryEntry(
        profileId: Long = 1,
        changedAt: OffsetDateTime = FIXED_DATE,
        changes: Map<String, Any?> = emptyMap(),
    ) = ProfileHistoryEntry(
        profileId = profileId,
        changedAt = changedAt,
        changes = changes,
    )
}

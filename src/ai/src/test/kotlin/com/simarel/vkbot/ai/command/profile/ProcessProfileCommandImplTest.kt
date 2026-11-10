package com.simarel.vkbot.ai.command.profile

import com.simarel.vkbot.ai.domain.exception.ProfileGenerationException
import com.simarel.vkbot.ai.port.output.profile.ProfileAnswerGenerationPort
import com.simarel.vkbot.ai.port.output.profile.ProfileAnswerGenerationRequest
import com.simarel.vkbot.ai.port.output.profile.ProfileAnswerGenerationResponse
import com.simarel.vkbot.ai.testfixtures.FakeConversationContextPortProvider
import com.simarel.vkbot.ai.testfixtures.FakeProfileProvider
import com.simarel.vkbot.share.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.ChatAuthor
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.ProfileHistoryEntry
import com.simarel.vkbot.share.domain.model.ResponseMessage
import com.simarel.vkbot.share.domain.model.UserStatistics
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.port.output.persistence.FindChatAuthorsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfileHistoryPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserStatisticsPort
import com.simarel.vkbot.share.port.output.vk.GetGroupProfilesBatchPort
import com.simarel.vkbot.share.port.output.vk.GetUserProfilesBatchPort
import com.simarel.vkbot.testfixtures.command.vkFacade.FakePublishEventCommand
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import com.simarel.vkbot.testfixtures.port.FakeProfilePortProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

private const val BOT_ID = 9_000_001L
private const val BOT_MENTION = "@simarel"
private const val GROUP_ID = 232_142_875L
private const val PEER_ID = 2_600_000_001L
private const val AUTHOR_ID = 7_600_009L
private const val MESSAGE_ID = 500L
private const val STATISTICS_TIMEZONE = "Europe/Minsk"
private const val ANSWER_TEXT = "profile answer"
private const val NO_TARGET_REPLY = "Уточните, о ком речь: имя, @упоминание, id или пересланное сообщение."
private val REQUEST_DATE: OffsetDateTime = OffsetDateTime.parse("2026-11-10T12:00:00Z")

class ProcessProfileCommandImplTest {

    @Test
    fun `resolves a single user mention and publishes one answer`() {
        val message = message("что ты знаешь о [id7600001|@pasha]")
        val publish = FakePublishEventCommand()
        val generation = RecordingProfileAnswerGenerationPort()
        val storedUserProfiles = RecordingFindStoredUserProfilesByIdsPort(listOf(userProfile(7600001)))

        command(
            publish = publish,
            storedUserProfiles = storedUserProfiles,
            generation = generation,
        ).execute(FakeProfileProvider.createProcessProfileCommandRequest(message))

        assertEquals(listOf(FakeVoProvider.createFromId(7600001)), storedUserProfiles.requests.single().ids)
        assertEquals(1, generation.requests.size)
        assertEquals(message.messageText.value, generation.requests.single().question)
        assertSingleAnswer(publish, ANSWER_TEXT, message)
    }

    @ParameterizedTest
    @ValueSource(strings = ["[club987654321|@testgroup]", "[public987654321|@testgroup]"])
    fun `resolves a group mention to a negative group id`(text: String) {
        val message = message(text)
        val publish = FakePublishEventCommand()
        val generation = RecordingProfileAnswerGenerationPort()
        val storedGroupProfiles = RecordingFindStoredGroupProfilesByIdsPort(
            listOf(FakeModelProvider.createVkGroupProfile(id = -987654321)),
        )
        val statistics = RecordingFindStoredUserStatisticsPort()
        val history = RecordingFindStoredUserProfileHistoryPort()

        command(
            publish = publish,
            storedGroupProfiles = storedGroupProfiles,
            statistics = statistics,
            history = history,
            generation = generation,
        ).execute(FakeProfileProvider.createProcessProfileCommandRequest(message))

        assertEquals(
            FakePersistencePortProvider.createFindGroupProfilesByIdsRequest(
                ids = listOf(FakeVoProvider.createFromId(-987654321)),
            ),
            storedGroupProfiles.requests.single(),
        )
        assertTrue(statistics.requests.isEmpty())
        assertTrue(history.requests.isEmpty())
        assertEquals(1, generation.requests.size)
        assertSingleAnswer(publish, ANSWER_TEXT, message)
    }

    @Test
    fun `uses the top forwarded message author when no mention is present`() {
        listOf(
            listOf(forwarded(7000002), forwarded(BOT_ID)),
            listOf(forwarded(BOT_ID), forwarded(7000002)),
        ).forEach { forwardedMessages ->
            val message = message("что нового", forwardedMessages = forwardedMessages)
            val publish = FakePublishEventCommand()
            val generation = RecordingProfileAnswerGenerationPort()
            val storedUserProfiles = RecordingFindStoredUserProfilesByIdsPort(listOf(userProfile(7000002)))

            command(
                publish = publish,
                storedUserProfiles = storedUserProfiles,
                generation = generation,
            ).execute(FakeProfileProvider.createProcessProfileCommandRequest(message))

            assertEquals(listOf(FakeVoProvider.createFromId(7000002)), storedUserProfiles.requests.single().ids)
            assertEquals(1, generation.requests.size)
            assertSingleAnswer(publish, ANSWER_TEXT, message)
        }
    }

    @Test
    fun `uses the last forwarded message author`() {
        val message = message("что нового", forwardedMessages = listOf(forwarded(7000002), forwarded(7000003)))
        val publish = FakePublishEventCommand()
        val generation = RecordingProfileAnswerGenerationPort()
        val storedUserProfiles = RecordingFindStoredUserProfilesByIdsPort(
            listOf(userProfile(7000002), userProfile(7000003)),
        )

        command(
            publish = publish,
            storedUserProfiles = storedUserProfiles,
            generation = generation,
        ).execute(FakeProfileProvider.createProcessProfileCommandRequest(message))

        assertEquals(listOf(FakeVoProvider.createFromId(7000003)), storedUserProfiles.requests.single().ids)
        assertEquals(1, generation.requests.size)
        assertSingleAnswer(publish, ANSWER_TEXT, message)
    }

    @Test
    fun `uses a forwarded group message author as a group`() {
        val message = message("что нового", forwardedMessages = listOf(forwarded(-232142876)))
        val publish = FakePublishEventCommand()
        val generation = RecordingProfileAnswerGenerationPort()
        val storedUserProfiles = RecordingFindStoredUserProfilesByIdsPort()
        val storedGroupProfiles = RecordingFindStoredGroupProfilesByIdsPort(
            listOf(FakeModelProvider.createVkGroupProfile(id = -232142876)),
        )
        val statistics = RecordingFindStoredUserStatisticsPort()
        val history = RecordingFindStoredUserProfileHistoryPort()

        command(
            publish = publish,
            storedUserProfiles = storedUserProfiles,
            storedGroupProfiles = storedGroupProfiles,
            statistics = statistics,
            history = history,
            generation = generation,
        ).execute(FakeProfileProvider.createProcessProfileCommandRequest(message))

        assertEquals(listOf(FakeVoProvider.createFromId(-232142876)), storedGroupProfiles.requests.single().ids)
        assertTrue(storedUserProfiles.requests.isEmpty())
        assertTrue(statistics.requests.isEmpty())
        assertTrue(history.requests.isEmpty())
        assertEquals(1, generation.requests.size)
        assertSingleAnswer(publish, ANSWER_TEXT, message)
    }

    @Test
    fun `skips a forwarded bot message`() {
        val message = message(
            "обо мне",
            fromId = 7600005,
            forwardedMessages = listOf(forwarded(BOT_ID), forwarded(BOT_ID)),
        )
        val publish = FakePublishEventCommand()
        val generation = RecordingProfileAnswerGenerationPort()
        val storedUserProfiles = RecordingFindStoredUserProfilesByIdsPort(listOf(userProfile(7600005)))

        command(
            publish = publish,
            storedUserProfiles = storedUserProfiles,
            generation = generation,
        ).execute(FakeProfileProvider.createProcessProfileCommandRequest(message))

        assertEquals(listOf(FakeVoProvider.createFromId(7600005)), storedUserProfiles.requests.single().ids)
        assertEquals(1, generation.requests.size)
        assertSingleAnswer(publish, ANSWER_TEXT, message)
    }

    @Test
    fun `resolves the phrase about me to the message author`() {
        val message = message("расскажи про меня", fromId = 7600003)
        val publish = FakePublishEventCommand()
        val generation = RecordingProfileAnswerGenerationPort()
        val storedUserProfiles = RecordingFindStoredUserProfilesByIdsPort(listOf(userProfile(7600003)))

        command(
            publish = publish,
            storedUserProfiles = storedUserProfiles,
            generation = generation,
        ).execute(FakeProfileProvider.createProcessProfileCommandRequest(message))

        assertEquals(listOf(FakeVoProvider.createFromId(7600003)), storedUserProfiles.requests.single().ids)
        assertEquals(1, generation.requests.size)
        assertSingleAnswer(publish, ANSWER_TEXT, message)
    }

    @Test
    fun `matches a unique participant by full name and case form`() {
        val message = message("что ты знаешь о Паше Селезневе")
        val publish = FakePublishEventCommand()
        val generation = RecordingProfileAnswerGenerationPort()
        val chatAuthors = RecordingFindChatAuthorsPort(
            listOf(FakeModelProvider.createChatAuthor(fromId = 7600001)),
        )
        val storedUserProfiles = RecordingFindStoredUserProfilesByIdsPort(
            listOf(userProfile(7600001, firstName = "Паша", lastName = "Селезнев", screenName = "pasha")),
        )
        val statistics = RecordingFindStoredUserStatisticsPort()

        command(
            publish = publish,
            chatAuthors = chatAuthors,
            storedUserProfiles = storedUserProfiles,
            statistics = statistics,
            generation = generation,
        ).execute(FakeProfileProvider.createProcessProfileCommandRequest(message))

        assertEquals(FakeVoProvider.createFromId(7600001), statistics.requests.single().userId)
        assertEquals(1, generation.requests.size)
        assertSingleAnswer(publish, ANSWER_TEXT, message)
    }

    @Test
    fun `asks to clarify when several participants match`() {
        val message = message("что ты знаешь о Паше")
        val publish = FakePublishEventCommand()
        val generation = RecordingProfileAnswerGenerationPort()
        val chatAuthors = RecordingFindChatAuthorsPort(
            listOf(
                FakeModelProvider.createChatAuthor(fromId = 7600002),
                FakeModelProvider.createChatAuthor(fromId = 7600001),
            ),
        )
        val storedUserProfiles = RecordingFindStoredUserProfilesByIdsPort(
            listOf(
                userProfile(7600002, firstName = "Паша", lastName = "Иванов", screenName = "pasha2"),
                userProfile(7600001, firstName = "Паша", lastName = "Селезнев", screenName = "pasha1"),
            ),
        )

        command(
            publish = publish,
            chatAuthors = chatAuthors,
            storedUserProfiles = storedUserProfiles,
            generation = generation,
        ).execute(FakeProfileProvider.createProcessProfileCommandRequest(message))

        val clarification = publishedText(publish)
        assertEquals(
            "Уточните, о ком речь:\n" +
                "1) Паша Селезнев (@pasha1, id7600001); 2) Паша Иванов (@pasha2, id7600002)\n" +
                "Напишите полное имя или упоминание.",
            clarification,
        )
        assertTrue(clarification.contains("1)"))
        assertTrue(clarification.contains("@"))
        assertTrue(clarification.contains("id7600001"))
        assertTrue(clarification.endsWith("Напишите полное имя или упоминание."))
        assertNoGeneration(generation)
    }

    @Test
    fun `lists at most ten candidates`() {
        val mentions = (1..12).joinToString(" ") { index -> "[id${7600100 + index}|@person$index]" }
        val message = message(mentions)
        val publish = FakePublishEventCommand()
        val generation = RecordingProfileAnswerGenerationPort()

        command(publish = publish, generation = generation)
            .execute(FakeProfileProvider.createProcessProfileCommandRequest(message))

        val clarification = publishedText(publish)
        assertEquals(10, clarification.lines()[1].split("; ").size)
        assertTrue(clarification.contains("10)"))
        assertFalse(clarification.contains("11)"))
        assertNoGeneration(generation)
    }

    @Test
    fun `reports not found for an unknown name`() {
        val message = message("кто такой Вася Пупкин")
        val publish = FakePublishEventCommand()
        val generation = RecordingProfileAnswerGenerationPort()
        val chatAuthors = RecordingFindChatAuthorsPort(
            listOf(FakeModelProvider.createChatAuthor(fromId = 7600001)),
        )
        val storedUserProfiles = RecordingFindStoredUserProfilesByIdsPort(
            listOf(userProfile(7600001, firstName = "Паша", lastName = "Селезнев", screenName = "pasha")),
        )

        command(
            publish = publish,
            chatAuthors = chatAuthors,
            storedUserProfiles = storedUserProfiles,
            generation = generation,
        ).execute(FakeProfileProvider.createProcessProfileCommandRequest(message))

        assertEquals(
            "Среди участников этого чата не нашёл «Вася Пупкин». " +
                "Напишите полное имя, @упоминание, id или перешлите сообщение.",
            publishedText(publish),
        )
        assertNoGeneration(generation)
    }

    @Test
    fun `asks for a target when nothing is resolvable`() {
        val message = message("что ты знаешь")
        val publish = FakePublishEventCommand()
        val generation = RecordingProfileAnswerGenerationPort()

        command(publish = publish, generation = generation)
            .execute(FakeProfileProvider.createProcessProfileCommandRequest(message))

        assertSingleAnswer(publish, NO_TARGET_REPLY, message)
        assertNoGeneration(generation)
    }

    @Test
    fun `feeds profile, statistics and history into the model and publishes its answer`() {
        val message = message("что ты знаешь о [id7600001|@pasha]")
        val publish = FakePublishEventCommand()
        val generation = RecordingProfileAnswerGenerationPort()
        val chatAuthors = RecordingFindChatAuthorsPort(
            listOf(FakeModelProvider.createChatAuthor(fromId = 7600001, messageCount = 42)),
        )
        val storedUserProfiles = RecordingFindStoredUserProfilesByIdsPort(listOf(userProfile(7600001)))
        val statistics = RecordingFindStoredUserStatisticsPort(
            FakeModelProvider.createUserStatistics(
                userId = 7600001,
                messageCount = 10,
                totalLength = 100,
                averageLength = 10.0,
                firstMessageAt = REQUEST_DATE.minusDays(2),
                lastMessageAt = REQUEST_DATE,
                chats = listOf(
                    FakeModelProvider.createChatStatistics(
                        peerId = PEER_ID,
                        messageCount = 3,
                        totalLength = 30,
                        averageLength = 10.0,
                    ),
                ),
                activityByHour = listOf(FakeModelProvider.createHourActivity(hour = 12, messageCount = 10)),
                activityByDayOfWeek = listOf(
                    FakeModelProvider.createDayOfWeekActivity(dayOfWeek = 1, messageCount = 10),
                ),
            ),
        )
        val history = RecordingFindStoredUserProfileHistoryPort(
            listOf(
                FakeModelProvider.createProfileHistoryEntry(
                    profileId = 7600001,
                    changedAt = REQUEST_DATE,
                    changes = mapOf("city" to mapOf("old" to "Moscow", "new" to "Minsk")),
                ),
            ),
        )

        command(
            publish = publish,
            chatAuthors = chatAuthors,
            storedUserProfiles = storedUserProfiles,
            statistics = statistics,
            history = history,
            generation = generation,
        ).execute(FakeProfileProvider.createProcessProfileCommandRequest(message))

        val request = generation.requests.single()
        assertEquals(message.messageText.value, request.question)
        assertEquals(20, history.requests.single().limit)
        listOf("Профиль:", "Статистика:", "История изменений:", "В этом чате:").forEach { header ->
            assertTrue(request.data.contains(header), "dossier data misses $header")
        }
        assertSingleAnswer(publish, ANSWER_TEXT, message)
    }

    @Test
    fun `the command constructor has no write port`() {
        val portAndCommandTypes = ProcessProfileCommandImpl::class.java.declaredConstructors
            .single()
            .parameterTypes
            .filter { type -> type.name.contains(".port.") || type.name.contains(".command.") }

        val persistencePorts = portAndCommandTypes
            .filter { type -> type.name.contains(".port.output.persistence.") }
            .map { type -> type.simpleName }
        val commandTypes = portAndCommandTypes
            .filter { type -> type.name.contains(".command.") }
            .map { type -> type.simpleName }

        assertTrue(persistencePorts.isNotEmpty())
        assertTrue(persistencePorts.all { simpleName -> simpleName.startsWith("Find") })
        assertEquals(listOf("PublishEventCommand"), commandTypes)
    }

    @Test
    fun `publishes the fallback when the model fails and logs no PII`() {
        val message = message("что ты знаешь о [id7600001|@pasha]")
        val publish = FakePublishEventCommand()
        val generation = RecordingProfileAnswerGenerationPort(
            failure = ProfileGenerationException("boom", null),
        )
        val storedUserProfiles = RecordingFindStoredUserProfilesByIdsPort(listOf(userProfile(7600001)))
        val history = RecordingFindStoredUserProfileHistoryPort(
            listOf(
                FakeModelProvider.createProfileHistoryEntry(
                    profileId = 7600001,
                    changedAt = REQUEST_DATE,
                    changes = mapOf("city" to mapOf("old" to "Moscow", "new" to "Минск")),
                ),
            ),
        )

        JulCapture(ProcessProfileCommandImpl::class.java.name).use { logs ->
            command(
                publish = publish,
                storedUserProfiles = storedUserProfiles,
                history = history,
                generation = generation,
            ).execute(FakeProfileProvider.createProcessProfileCommandRequest(message))

            val logged = logs.records.single()
            assertEquals(
                "Profile answer generation failed for peerId=2600000001: ProfileGenerationException",
                logged.message,
            )
            assertEquals(Level.SEVERE, logged.level)
        }

        val published = publishedText(publish)
        assertEquals("Не удалось собрать профиль. Попробуйте позже.", published)
        assertFalse(published.contains("Паша"))
        assertFalse(published.contains("Минск"))
        assertFalse(published.contains(message.messageText.value))
        assertEquals(1, generation.requests.size)
    }

    @Test
    fun `fetches a missing profile through the vk port and reports when it is unavailable`() {
        val message = message("что ты знаешь о [id7600007|@nobody]")
        val publish = FakePublishEventCommand()
        val generation = RecordingProfileAnswerGenerationPort()
        val userProfilesBatch = RecordingGetUserProfilesBatchPort()

        command(
            publish = publish,
            userProfilesBatch = userProfilesBatch,
            generation = generation,
        ).execute(FakeProfileProvider.createProcessProfileCommandRequest(message))

        assertEquals(
            FakeProfilePortProvider.createGetUserProfilesBatchRequest(
                fromIds = listOf(FakeVoProvider.createFromId(7600007)),
            ),
            userProfilesBatch.requests.single(),
        )
        assertSingleAnswer(publish, "Профиль недоступен.", message)
        assertNoGeneration(generation)
    }

    @Test
    fun `fetches a missing group profile through the vk port`() {
        val message = message("[club987654322|@othergroup]")
        val publish = FakePublishEventCommand()
        val generation = RecordingProfileAnswerGenerationPort()
        val storedGroupProfiles = RecordingFindStoredGroupProfilesByIdsPort()
        val groupProfilesBatch = RecordingGetGroupProfilesBatchPort(
            listOf(FakeModelProvider.createVkGroupProfile(id = -987654322)),
        )
        val statistics = RecordingFindStoredUserStatisticsPort()
        val history = RecordingFindStoredUserProfileHistoryPort()

        command(
            publish = publish,
            storedGroupProfiles = storedGroupProfiles,
            statistics = statistics,
            history = history,
            groupProfilesBatch = groupProfilesBatch,
            generation = generation,
        ).execute(FakeProfileProvider.createProcessProfileCommandRequest(message))

        assertEquals(
            FakeProfilePortProvider.createGetGroupProfilesBatchRequest(
                fromIds = listOf(FakeVoProvider.createFromId(-987654322)),
            ),
            groupProfilesBatch.requests.single(),
        )
        assertEquals(1, generation.requests.size)
        assertSingleAnswer(publish, ANSWER_TEXT, message)
        assertTrue(statistics.requests.isEmpty())
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "[club232142875|@simarel]",
            "[id9000001|@bot]",
            "[id9000001|@simarel]",
            "[id7600009|@simarel]",
            "[club232142875|@community]",
        ],
    )
    fun `ignores a mention of the bot itself`(text: String) {
        val message = message(text)
        val publish = FakePublishEventCommand()
        val generation = RecordingProfileAnswerGenerationPort()

        command(publish = publish, generation = generation)
            .execute(FakeProfileProvider.createProcessProfileCommandRequest(message))

        assertSingleAnswer(publish, NO_TARGET_REPLY, message)
        assertNoGeneration(generation)
    }

    private fun publishedText(publish: FakePublishEventCommand): String {
        val response = publish.executeCalls.single().payload.value as ResponseMessage
        return response.messageText.value
    }

    private fun assertSingleAnswer(publish: FakePublishEventCommand, text: String, message: Message) {
        val request = publish.executeCalls.single()
        assertEquals(Event.ANSWER_MESSAGE, request.event)
        val response = request.payload.value as ResponseMessage
        assertEquals(text, response.messageText.value)
        assertSame(message, response.responseTo)
    }

    private fun assertNoGeneration(generation: RecordingProfileAnswerGenerationPort) {
        assertTrue(generation.requests.isEmpty())
    }

    private fun message(
        text: String,
        fromId: Long = AUTHOR_ID,
        forwardedMessages: List<Message> = emptyList(),
    ): Message = FakeVoProvider.createMessage(
        date = FakeVoProvider.createDate(REQUEST_DATE),
        fromId = FakeVoProvider.createFromId(fromId),
        groupId = FakeVoProvider.createGroupId(GROUP_ID),
        peerId = FakeVoProvider.createPeerId(PEER_ID),
        conversationMessageId = FakeVoProvider.createConversationMessageId(MESSAGE_ID),
        messageText = FakeVoProvider.createMessageText(text),
        forwardedMessages = forwardedMessages,
    )

    private fun forwarded(fromId: Long): Message = message("пересланное сообщение", fromId = fromId)

    private fun userProfile(
        id: Long,
        firstName: String? = "Паша",
        lastName: String? = "Селезнев",
        screenName: String? = "pasha",
        city: String? = "Минск",
    ): VkUserProfile = FakeModelProvider.createVkUserProfile(
        id = id,
        firstName = firstName,
        lastName = lastName,
        screenName = screenName,
        city = city,
    )

    @Suppress("LongParameterList")
    private fun command(
        publish: PublishEventCommand = FakePublishEventCommand(),
        chatAuthors: FindChatAuthorsPort = RecordingFindChatAuthorsPort(),
        storedUserProfiles: FindStoredUserProfilesByIdsPort = RecordingFindStoredUserProfilesByIdsPort(),
        storedGroupProfiles: FindStoredGroupProfilesByIdsPort = RecordingFindStoredGroupProfilesByIdsPort(),
        statistics: FindStoredUserStatisticsPort = RecordingFindStoredUserStatisticsPort(),
        history: FindStoredUserProfileHistoryPort = RecordingFindStoredUserProfileHistoryPort(),
        userProfilesBatch: GetUserProfilesBatchPort = RecordingGetUserProfilesBatchPort(),
        groupProfilesBatch: GetGroupProfilesBatchPort = RecordingGetGroupProfilesBatchPort(),
        generation: ProfileAnswerGenerationPort = RecordingProfileAnswerGenerationPort(),
    ) = ProcessProfileCommandImpl(
        publishEventCommand = publish,
        findChatAuthorsPort = chatAuthors,
        findStoredUserProfilesByIdsPort = storedUserProfiles,
        findStoredGroupProfilesByIdsPort = storedGroupProfiles,
        findStoredUserStatisticsPort = statistics,
        findStoredUserProfileHistoryPort = history,
        getUserProfilesBatchPort = userProfilesBatch,
        getGroupProfilesBatchPort = groupProfilesBatch,
        profileAnswerGenerationPort = generation,
        botId = BOT_ID,
        botMention = BOT_MENTION,
        groupId = GROUP_ID,
        statisticsTimezone = STATISTICS_TIMEZONE,
    )

    private class RecordingFindChatAuthorsPort(
        private val authors: List<ChatAuthor> = emptyList(),
    ) : FindChatAuthorsPort {
        val requests = ConcurrentLinkedQueue<FindChatAuthorsPort.Request>()

        override fun execute(request: FindChatAuthorsPort.Request): FindChatAuthorsPort.Response {
            requests.add(request)
            return FakeProfileProvider.createFindChatAuthorsResponse(authors)
        }
    }

    private class RecordingFindStoredUserProfilesByIdsPort(
        private val profiles: List<VkUserProfile> = emptyList(),
    ) : FindStoredUserProfilesByIdsPort {
        val requests = ConcurrentLinkedQueue<FindStoredUserProfilesByIdsPort.Request>()

        override fun execute(
            request: FindStoredUserProfilesByIdsPort.Request,
        ): FindStoredUserProfilesByIdsPort.Response {
            requests.add(request)
            return FakeConversationContextPortProvider.createFindUserProfilesByIdsResponse(profiles)
        }
    }

    private class RecordingFindStoredGroupProfilesByIdsPort(
        private val profiles: List<VkGroupProfile> = emptyList(),
    ) : FindStoredGroupProfilesByIdsPort {
        val requests = ConcurrentLinkedQueue<FindStoredGroupProfilesByIdsPort.Request>()

        override fun execute(
            request: FindStoredGroupProfilesByIdsPort.Request,
        ): FindStoredGroupProfilesByIdsPort.Response {
            requests.add(request)
            return FakeConversationContextPortProvider.createFindGroupProfilesByIdsResponse(profiles)
        }
    }

    private class RecordingFindStoredUserStatisticsPort(
        private val statistics: UserStatistics = FakeModelProvider.createUserStatistics(),
    ) : FindStoredUserStatisticsPort {
        val requests = ConcurrentLinkedQueue<FindStoredUserStatisticsPort.Request>()

        override fun execute(
            request: FindStoredUserStatisticsPort.Request,
        ): FindStoredUserStatisticsPort.Response {
            requests.add(request)
            return FakeProfileProvider.createFindStoredUserStatisticsResponse(statistics)
        }
    }

    private class RecordingFindStoredUserProfileHistoryPort(
        private val entries: List<ProfileHistoryEntry> = emptyList(),
    ) : FindStoredUserProfileHistoryPort {
        val requests = ConcurrentLinkedQueue<FindStoredUserProfileHistoryPort.Request>()

        override fun execute(
            request: FindStoredUserProfileHistoryPort.Request,
        ): FindStoredUserProfileHistoryPort.Response {
            requests.add(request)
            return FakeProfileProvider.createFindStoredUserProfileHistoryResponse(entries)
        }
    }

    private class RecordingGetUserProfilesBatchPort(
        private val profiles: List<VkUserProfile> = emptyList(),
    ) : GetUserProfilesBatchPort {
        val requests = ConcurrentLinkedQueue<GetUserProfilesBatchPort.Request>()

        override fun execute(request: GetUserProfilesBatchPort.Request): GetUserProfilesBatchPort.Response {
            requests.add(request)
            return FakeProfileProvider.createGetUserProfilesBatchResponse(profiles)
        }
    }

    private class RecordingGetGroupProfilesBatchPort(
        private val profiles: List<VkGroupProfile> = emptyList(),
    ) : GetGroupProfilesBatchPort {
        val requests = ConcurrentLinkedQueue<GetGroupProfilesBatchPort.Request>()

        override fun execute(request: GetGroupProfilesBatchPort.Request): GetGroupProfilesBatchPort.Response {
            requests.add(request)
            return FakeProfileProvider.createGetGroupProfilesBatchResponse(profiles)
        }
    }

    private class RecordingProfileAnswerGenerationPort(
        private val text: String = ANSWER_TEXT,
        private val failure: Exception? = null,
    ) : ProfileAnswerGenerationPort {
        val requests = ConcurrentLinkedQueue<ProfileAnswerGenerationRequest>()

        override fun execute(request: ProfileAnswerGenerationRequest): ProfileAnswerGenerationResponse {
            requests.add(request)
            failure?.let { throw it }
            return FakeProfileProvider.createProfileAnswerGenerationResponse(text)
        }
    }

    private class JulCapture(loggerName: String) : AutoCloseable {
        private val logger = Logger.getLogger(loggerName)
        private val previousLevel: Level? = logger.level
        val records = CopyOnWriteArrayList<LogRecord>()
        private val handler = object : Handler() {
            override fun publish(record: LogRecord) {
                records += record
            }

            override fun flush() = Unit

            override fun close() = Unit
        }

        init {
            logger.level = Level.ALL
            logger.addHandler(handler)
        }

        override fun close() {
            logger.removeHandler(handler)
            logger.level = previousLevel
        }
    }
}

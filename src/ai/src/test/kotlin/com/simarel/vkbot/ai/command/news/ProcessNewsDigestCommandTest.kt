package com.simarel.vkbot.ai.command.news

import com.simarel.vkbot.ai.command.common.MessageAuthorFormatter
import com.simarel.vkbot.ai.domain.exception.NewsDigestGenerationException
import com.simarel.vkbot.ai.port.output.news.NewsDigestGenerationPort
import com.simarel.vkbot.ai.port.output.news.NewsDigestGenerationRequest
import com.simarel.vkbot.ai.port.output.news.NewsDigestGenerationResponse
import com.simarel.vkbot.ai.test.SystemLoggerCapture
import com.simarel.vkbot.ai.testfixtures.FakeConversationContextPortProvider
import com.simarel.vkbot.ai.testfixtures.FakeNewsDigestProvider
import com.simarel.vkbot.ai.testfixtures.port.knowledge.FakeFindChatKnowledgeSummaryPort
import com.simarel.vkbot.ai.testfixtures.port.knowledge.FakeFindUserKnowledgePort
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.Summary
import com.simarel.vkbot.share.domain.model.VkGroupProfile
import com.simarel.vkbot.share.domain.model.VkUserProfile
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import com.simarel.vkbot.share.port.output.persistence.FindChatKnowledgeSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindLastStoredMessageByAuthorPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredGroupProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredMessagesBetweenDatesPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredSummariesInPeriodPort
import com.simarel.vkbot.share.port.output.persistence.FindStoredUserProfilesByIdsPort
import com.simarel.vkbot.share.port.output.persistence.FindUserKnowledgePort
import com.simarel.vkbot.testfixtures.command.vkFacade.FakePublishEventCommand
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.time.OffsetDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.logging.Level
import java.util.stream.Stream

private const val PEER_ID = 2_000_000_001L
private const val FROM_ID = 777L
private const val REQUEST_MESSAGE_ID = 500L
private const val SUMMARY_BATCH_SIZE = 100
private val REQUEST_DATE: OffsetDateTime = OffsetDateTime.parse("2026-11-10T12:00:00Z")
private val EXPLICIT_FROM: OffsetDateTime = OffsetDateTime.parse("2026-11-01T00:00:00Z")
private val EXPLICIT_TO: OffsetDateTime = OffsetDateTime.parse("2026-11-03T23:59:59.999999999Z")
private val WEEK_AGO: OffsetDateTime = REQUEST_DATE.minusDays(7)

private const val TOO_LONG_TEXT = "Слишком длинный срок: сводка возможна максимум за неделю."
private const val NO_MESSAGES_TEXT = "Нет сообщений за этот период."
private const val FALLBACK_TEXT = "Не удалось собрать сводку. Попробуйте позже."
private const val AUTHOR_SUMMARY = "любит футбол"
private const val AUTHOR_INTEREST = "коллекционирует винил"
private const val STALE_SUMMARY = "старая сводка"
private const val CHAT_TOPICS = "обсуждали релиз"

@Suppress("LargeClass")
class ProcessNewsDigestCommandTest {

    @Test
    fun `execute replies with the too long text for eight days and calls no ports`() {
        val message = requestMessage("что нового за 8 дней")
        val publish = FakePublishEventCommand()
        val findLast = RecordingFindLastStoredMessageByAuthorPort()
        val findSummaries = RecordingFindStoredSummariesInPeriodPort()
        val findMessages = RecordingFindStoredMessagesBetweenDatesPort()
        val generation = RecordingNewsDigestGenerationPort()
        val findUserProfiles = RecordingFindUserProfilesByIdsPort()
        val findGroupProfiles = RecordingFindGroupProfilesByIdsPort()
        val findUserKnowledge = FakeFindUserKnowledgePort()
        val findChatKnowledge = FakeFindChatKnowledgeSummaryPort()

        command(
            publish,
            findLast,
            findSummaries,
            findMessages,
            generation,
            findUserProfiles,
            findGroupProfiles,
            findUserKnowledge,
            findChatKnowledge,
        ).execute(FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message))

        assertAnswer(publish, TOO_LONG_TEXT, message)
        assertTrue(findLast.requests.isEmpty())
        assertTrue(findSummaries.requests.isEmpty())
        assertTrue(findMessages.requests.isEmpty())
        assertTrue(generation.requests.isEmpty())
        assertTrue(findUserProfiles.requests.isEmpty())
        assertTrue(findGroupProfiles.requests.isEmpty())
        assertTrue(findUserKnowledge.executeCalls.isEmpty())
        assertTrue(findChatKnowledge.executeCalls.isEmpty())
    }

    @Test
    fun `execute replies with the too long text for eight calendar days`() {
        val message = requestMessage("что нового с 1 по 8 ноября")
        val publish = FakePublishEventCommand()
        val findSummaries = RecordingFindStoredSummariesInPeriodPort()

        command(publish = publish, findSummaries = findSummaries).execute(
            FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message),
        )

        assertAnswer(publish, TOO_LONG_TEXT, message)
        assertTrue(findSummaries.requests.isEmpty())
    }

    @Test
    fun `execute runs the digest for exactly seven days`() {
        val message = requestMessage("что нового за 7 дней")
        val publish = FakePublishEventCommand()
        val generation = RecordingNewsDigestGenerationPort(text = "seven days digest")
        val findMessages = RecordingFindStoredMessagesBetweenDatesPort(
            listOf(storedMessage(conversationMessageId = 501)),
        )

        command(publish = publish, findMessages = findMessages, generation = generation).execute(
            FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message),
        )

        assertEquals(1, generation.requests.size)
        assertAnswer(publish, "seven days digest", message)
    }

    @Test
    fun `execute runs the digest for seven calendar days`() {
        val message = requestMessage("что нового с 1 по 7 ноября")
        val publish = FakePublishEventCommand()
        val generation = RecordingNewsDigestGenerationPort(text = "calendar digest")
        val findMessages = RecordingFindStoredMessagesBetweenDatesPort(
            listOf(storedMessage(conversationMessageId = 501)),
        )

        command(publish = publish, findMessages = findMessages, generation = generation).execute(
            FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message),
        )

        assertEquals(1, generation.requests.size)
        assertAnswer(publish, "calendar digest", message)
    }

    @ParameterizedTest
    @MethodSource("lastAuthorMessageCases")
    fun `execute resolves the period without an explicit term from the last author message`(
        lastMessageDate: OffsetDateTime?,
        expectedFrom: OffsetDateTime,
    ) {
        val message = requestMessage("что нового")
        val findLast = RecordingFindLastStoredMessageByAuthorPort(
            lastMessageDate?.let { storedMessage(conversationMessageId = 400, date = it) },
        )
        val findSummaries = RecordingFindStoredSummariesInPeriodPort()

        command(findLast = findLast, findSummaries = findSummaries).execute(
            FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message),
        )

        assertEquals(
            FakePersistencePortProvider.createFindLastStoredMessageByAuthorRequest(
                peerId = PEER_ID,
                fromId = FROM_ID,
                beforeConversationMessageId = REQUEST_MESSAGE_ID,
            ),
            findLast.requests.single(),
        )
        assertEquals(
            FakePersistencePortProvider.createFindStoredSummariesInPeriodRequest(PEER_ID, expectedFrom, REQUEST_DATE),
            findSummaries.requests.single(),
        )
    }

    @Test
    fun `execute builds the digest data from period summaries and fresh messages`() {
        val message = requestMessage("что нового с 1 по 3 ноября")
        val publish = FakePublishEventCommand()
        val findSummaries = RecordingFindStoredSummariesInPeriodPort(
            listOf(
                summary(firstMessageId = 10, lastMessageId = 20, shortSummary = "summary one"),
                summary(firstMessageId = 21, lastMessageId = 25, shortSummary = "   "),
            ),
        )
        val findMessages = RecordingFindStoredMessagesBetweenDatesPort(
            listOf(
                storedMessage(conversationMessageId = 15, fromId = 5, messageText = "old"),
                storedMessage(conversationMessageId = 26, fromId = 5, messageText = "hello"),
                storedMessage(conversationMessageId = 27, fromId = -8, messageText = "hi"),
                storedMessage(conversationMessageId = REQUEST_MESSAGE_ID, fromId = FROM_ID, messageText = "question"),
            ),
        )
        val generation = RecordingNewsDigestGenerationPort(text = "llm digest")
        val findUserProfiles = RecordingFindUserProfilesByIdsPort(
            listOf(FakeModelProvider.createVkUserProfile(id = 5, firstName = "Ivan", lastName = "Petrov")),
        )
        val findGroupProfiles = RecordingFindGroupProfilesByIdsPort(
            listOf(FakeModelProvider.createVkGroupProfile(id = 8, name = "Kotlin Group")),
        )

        command(
            publish = publish,
            findSummaries = findSummaries,
            findMessages = findMessages,
            generation = generation,
            findUserProfiles = findUserProfiles,
            findGroupProfiles = findGroupProfiles,
        ).execute(FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message))

        assertDataRequests(findSummaries, findMessages, findUserProfiles, findGroupProfiles)
        assertEquals(
            """
            Сводки обсуждений за период:
            summary one

            Сообщения за период:
            [Ivan Petrov]: hello
            [Kotlin Group]: hi
            """.trimIndent(),
            generation.requests.single().data,
        )
        assertAnswer(publish, "llm digest", message)
    }

    @Test
    fun `execute skips the request message when there are no summaries`() {
        val message = requestMessage("что нового с 1 по 3 ноября")
        val publish = FakePublishEventCommand()
        val generation = RecordingNewsDigestGenerationPort()
        val findMessages = RecordingFindStoredMessagesBetweenDatesPort(
            listOf(storedMessage(conversationMessageId = REQUEST_MESSAGE_ID)),
        )

        command(publish = publish, findMessages = findMessages, generation = generation).execute(
            FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message),
        )

        assertAnswer(publish, NO_MESSAGES_TEXT, message)
        assertTrue(generation.requests.isEmpty())
    }

    @Test
    fun `execute includes all period messages when summaries have no last message id`() {
        val message = requestMessage("что нового с 1 по 3 ноября")
        val publish = FakePublishEventCommand()
        val generation = RecordingNewsDigestGenerationPort(text = "llm digest")
        val findSummaries = RecordingFindStoredSummariesInPeriodPort(
            listOf(summary(firstMessageId = 10, lastMessageId = null, shortSummary = "summary one")),
        )
        val findMessages = RecordingFindStoredMessagesBetweenDatesPort(
            listOf(
                storedMessage(conversationMessageId = 15, fromId = 5, messageText = "hello"),
                storedMessage(conversationMessageId = 26, fromId = 6, messageText = "world"),
                storedMessage(conversationMessageId = REQUEST_MESSAGE_ID, fromId = FROM_ID, messageText = "question"),
            ),
        )

        command(publish = publish, findSummaries = findSummaries, findMessages = findMessages, generation = generation)
            .execute(FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message))

        assertEquals(
            """
            Сводки обсуждений за период:
            summary one

            Сообщения за период:
            [Пользователь 5]: hello
            [Пользователь 6]: world
            """.trimIndent(),
            generation.requests.single().data,
        )
        assertAnswer(publish, "llm digest", message)
    }

    @Test
    fun `execute sends only the summaries section when there are no fresh messages`() {
        val message = requestMessage("что нового с 1 по 3 ноября")
        val publish = FakePublishEventCommand()
        val generation = RecordingNewsDigestGenerationPort(text = "llm digest")
        val findSummaries = RecordingFindStoredSummariesInPeriodPort(
            listOf(
                summary(firstMessageId = 10, lastMessageId = 25, shortSummary = "summary one"),
                summary(firstMessageId = 10, lastMessageId = 25, shortSummary = null),
                summary(firstMessageId = 10, lastMessageId = 25, shortSummary = ""),
                summary(firstMessageId = 10, lastMessageId = 25, shortSummary = "   "),
            ),
        )
        val findMessages = RecordingFindStoredMessagesBetweenDatesPort(
            listOf(
                storedMessage(conversationMessageId = 15, fromId = 5),
                storedMessage(conversationMessageId = REQUEST_MESSAGE_ID, fromId = FROM_ID),
            ),
        )

        command(publish = publish, findSummaries = findSummaries, findMessages = findMessages, generation = generation)
            .execute(FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message))

        assertEquals("Сводки обсуждений за период:\nsummary one", generation.requests.single().data)
        assertAnswer(publish, "llm digest", message)
    }

    @Test
    fun `execute replies with the fallback text when the llm fails`() {
        val message = requestMessage("что нового за 3 дня")
        val publish = FakePublishEventCommand()
        val generation = RecordingNewsDigestGenerationPort(
            failure = NewsDigestGenerationException("model is down", null),
        )
        val findMessages = RecordingFindStoredMessagesBetweenDatesPort(
            listOf(storedMessage(conversationMessageId = 501)),
        )

        command(publish = publish, findMessages = findMessages, generation = generation).execute(
            FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message),
        )

        assertAnswer(publish, FALLBACK_TEXT, message)
        assertEquals(1, generation.requests.size)
    }

    @Test
    fun `execute propagates read failures to the broker`() {
        val message = requestMessage("что нового за 3 дня")
        val publish = FakePublishEventCommand()
        val failure = IllegalStateException("database is down")

        val thrown = assertThrows(IllegalStateException::class.java) {
            command(
                publish = publish,
                findSummaries = FailingFindStoredSummariesInPeriodPort(failure),
            ).execute(FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message))
        }

        assertSame(failure, thrown)
        assertTrue(publish.executeCalls.isEmpty())
    }

    @Test
    fun `execute replies with the no messages text for a future period`() {
        val message = requestMessage("что нового 5 ноября 2027")
        val publish = FakePublishEventCommand()
        val generation = RecordingNewsDigestGenerationPort()
        val findUserKnowledge = FakeFindUserKnowledgePort()
        val findChatKnowledge = FakeFindChatKnowledgeSummaryPort()

        command(
            publish = publish,
            generation = generation,
            findUserKnowledge = findUserKnowledge,
            findChatKnowledge = findChatKnowledge,
        ).execute(FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message))

        assertAnswer(publish, NO_MESSAGES_TEXT, message)
        assertTrue(generation.requests.isEmpty())
        assertTrue(findUserKnowledge.executeCalls.isEmpty())
        assertTrue(findChatKnowledge.executeCalls.isEmpty())
    }

    @ParameterizedTest
    @CsvSource("5, 'Пользователь 5'", "-8, 'Группа -8'")
    fun `execute falls back to author placeholders when profiles are missing`(fromId: Long, expectedAuthor: String) {
        val message = requestMessage("что нового за 3 дня")
        val generation = RecordingNewsDigestGenerationPort()
        val findMessages = RecordingFindStoredMessagesBetweenDatesPort(
            listOf(storedMessage(conversationMessageId = 501, fromId = fromId, messageText = "hello")),
        )

        command(findMessages = findMessages, generation = generation).execute(
            FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message),
        )

        assertEquals(
            "Сообщения за период:\n[$expectedAuthor]: hello",
            generation.requests.single().data,
        )
    }

    @Test
    fun `command has no summary write ports`() {
        val parameterTypes = ProcessNewsDigestCommandImpl::class.java.declaredConstructors
            .single()
            .parameterTypes
            .toSet()

        assertTrue(
            parameterTypes.none {
                it.simpleName in setOf(
                    "CreateStoredPendingSummaryPort",
                    "SaveStoredCompletedSummaryPort",
                    "MarkStoredSummaryFailedPort",
                )
            },
        )
    }

    @Test
    fun `execute passes the fresh author summary and chat topics to the request`() {
        val message = requestMessage("что нового за 3 дня")
        val publish = FakePublishEventCommand()
        val generation = RecordingNewsDigestGenerationPort(text = "llm digest")
        val findUserKnowledge = FakeFindUserKnowledgePort().apply {
            knowledge = mapOf(
                FakeVoProvider.createFromId(FROM_ID) to FakeModelProvider.createUserKnowledge(
                    summary = AUTHOR_SUMMARY,
                    summaryFresh = true,
                ),
            )
        }
        val findChatKnowledge = FakeFindChatKnowledgeSummaryPort().apply {
            knowledge = FakeModelProvider.createChatKnowledge(peerId = PEER_ID, summary = CHAT_TOPICS)
        }

        command(
            publish = publish,
            findSummaries = summariesWith("summary one"),
            generation = generation,
            findUserKnowledge = findUserKnowledge,
            findChatKnowledge = findChatKnowledge,
        ).execute(FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message))

        val request = generation.requests.single()
        assertEquals(AUTHOR_SUMMARY, request.interests)
        assertEquals(CHAT_TOPICS, request.chatTopics)
        assertEquals("Сводки обсуждений за период:\nsummary one", request.data)
        assertEquals(listOf(FakeVoProvider.createFromId(FROM_ID)), findUserKnowledge.executeCalls.single().userIds)
        assertEquals(PEER_ID, findChatKnowledge.executeCalls.single().peerId)
        assertEquals(1, publish.executeCalls.size)
    }

    @Test
    fun `execute passes confirmed interests when the profile summary is stale`() {
        val message = requestMessage("что нового за 3 дня")
        val generation = RecordingNewsDigestGenerationPort(text = "llm digest")
        val findUserKnowledge = FakeFindUserKnowledgePort().apply {
            knowledge = mapOf(
                FakeVoProvider.createFromId(FROM_ID) to FakeModelProvider.createUserKnowledge(
                    facts = listOf(
                        FakeModelProvider.createUserFact(value = "футбол", factKey = "interest_football"),
                        FakeModelProvider.createUserFact(value = "футбол", factKey = "interest_sport"),
                        FakeModelProvider.createUserFact(value = "хоккей", factKey = "interest_hockey"),
                        FakeModelProvider.createUserFact(value = "бег", category = UserFactCategory.HABIT),
                        FakeModelProvider.createUserFact(value = "шахматы", status = UserFactStatus.CANDIDATE),
                        FakeModelProvider.createUserFact(value = "плавание", status = UserFactStatus.HISTORICAL),
                        FakeModelProvider.createUserFact(value = "зарядка", stale = true),
                    ),
                    summary = STALE_SUMMARY,
                    summaryFresh = false,
                ),
            )
        }

        command(
            findSummaries = summariesWith("summary one"),
            generation = generation,
            findUserKnowledge = findUserKnowledge,
        ).execute(FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message))

        val interests = generation.requests.single().interests.orEmpty()
        assertEquals("- футбол\n- хоккей", interests)
        listOf("шахматы", "бег", "зарядка", "плавание", STALE_SUMMARY).forEach {
            assertTrue(!interests.contains(it))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   "])
    fun `execute falls back to confirmed interests when the fresh profile summary is blank`(summary: String) {
        val message = requestMessage("что нового за 3 дня")
        val generation = RecordingNewsDigestGenerationPort(text = "llm digest")
        val findUserKnowledge = FakeFindUserKnowledgePort().apply {
            knowledge = mapOf(
                FakeVoProvider.createFromId(FROM_ID) to FakeModelProvider.createUserKnowledge(
                    facts = listOf(FakeModelProvider.createUserFact(value = "футбол")),
                    summary = summary,
                    summaryFresh = true,
                ),
            )
        }

        command(
            findSummaries = summariesWith("summary one"),
            generation = generation,
            findUserKnowledge = findUserKnowledge,
        ).execute(FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message))

        assertEquals("- футбол", generation.requests.single().interests)
    }

    @Test
    fun `execute passes no interests when only candidate or historical interests exist`() {
        val message = requestMessage("что нового за 3 дня")
        val generation = RecordingNewsDigestGenerationPort(text = "llm digest")
        val findUserKnowledge = FakeFindUserKnowledgePort().apply {
            knowledge = mapOf(
                FakeVoProvider.createFromId(FROM_ID) to FakeModelProvider.createUserKnowledge(
                    facts = listOf(
                        FakeModelProvider.createUserFact(value = "бег", category = UserFactCategory.HABIT),
                        FakeModelProvider.createUserFact(value = "шахматы", status = UserFactStatus.CANDIDATE),
                        FakeModelProvider.createUserFact(value = "плавание", status = UserFactStatus.HISTORICAL),
                        FakeModelProvider.createUserFact(value = "зарядка", stale = true),
                    ),
                ),
            )
        }

        command(
            findSummaries = summariesWith("summary one"),
            generation = generation,
            findUserKnowledge = findUserKnowledge,
        ).execute(FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message))

        assertNull(generation.requests.single().interests)
    }

    @Test
    fun `execute passes no knowledge and keeps the digest data byte-identical`() {
        val message = requestMessage("что нового за 3 дня")
        val publish = FakePublishEventCommand()
        val generation = RecordingNewsDigestGenerationPort(text = "llm digest")
        val findUserKnowledge = FakeFindUserKnowledgePort()
        val findChatKnowledge = FakeFindChatKnowledgeSummaryPort()

        command(
            publish = publish,
            findSummaries = summariesWith("summary one"),
            generation = generation,
            findUserKnowledge = findUserKnowledge,
            findChatKnowledge = findChatKnowledge,
        ).execute(FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message))

        val request = generation.requests.single()
        assertEquals("Сводки обсуждений за период:\nsummary one", request.data)
        assertNull(request.interests)
        assertNull(request.chatTopics)
        assertEquals(1, publish.executeCalls.size)
    }

    @Test
    fun `execute skips knowledge reads when personalization is disabled`() {
        val message = requestMessage("что нового за 3 дня")
        val publish = FakePublishEventCommand()
        val generation = RecordingNewsDigestGenerationPort(text = "llm digest")
        val findUserKnowledge = FakeFindUserKnowledgePort()
        val findChatKnowledge = FakeFindChatKnowledgeSummaryPort()

        command(
            publish = publish,
            findSummaries = summariesWith("summary one"),
            generation = generation,
            findUserKnowledge = findUserKnowledge,
            findChatKnowledge = findChatKnowledge,
            personalizationEnabled = false,
        ).execute(FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message))

        val request = generation.requests.single()
        assertEquals(0, findUserKnowledge.executeCalls.size)
        assertEquals(0, findChatKnowledge.executeCalls.size)
        assertNull(request.interests)
        assertNull(request.chatTopics)
        assertEquals(1, publish.executeCalls.size)
    }

    @Test
    fun `execute keeps the digest when the author knowledge read fails`() {
        val message = requestMessage("что нового за 3 дня")
        val publish = FakePublishEventCommand()
        val generation = RecordingNewsDigestGenerationPort(text = "llm digest")
        val findUserKnowledge = FakeFindUserKnowledgePort().apply {
            failure = IllegalStateException("knowledge db down")
        }
        val findChatKnowledge = FakeFindChatKnowledgeSummaryPort().apply {
            knowledge = FakeModelProvider.createChatKnowledge(peerId = PEER_ID, summary = CHAT_TOPICS)
        }

        SystemLoggerCapture(ProcessNewsDigestCommandImpl::class.java.name).use { capture ->
            command(
                publish = publish,
                findSummaries = summariesWith("summary one"),
                generation = generation,
                findUserKnowledge = findUserKnowledge,
                findChatKnowledge = findChatKnowledge,
            ).execute(FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message))

            val warning = capture.messages(Level.WARNING).single()
            assertTrue(warning.contains("peerId=$PEER_ID"))
            assertTrue(warning.contains("authorId=$FROM_ID"))
            assertTrue(warning.contains("IllegalStateException"))
        }

        val request = generation.requests.single()
        assertNull(request.interests)
        assertEquals(CHAT_TOPICS, request.chatTopics)
        assertEquals(1, publish.executeCalls.size)
    }

    @Test
    fun `execute keeps the digest when the chat knowledge read fails`() {
        val message = requestMessage("что нового за 3 дня")
        val publish = FakePublishEventCommand()
        val generation = RecordingNewsDigestGenerationPort(text = "llm digest")
        val findUserKnowledge = FakeFindUserKnowledgePort().apply {
            knowledge = mapOf(
                FakeVoProvider.createFromId(FROM_ID) to FakeModelProvider.createUserKnowledge(
                    summary = AUTHOR_SUMMARY,
                    summaryFresh = true,
                ),
            )
        }
        val findChatKnowledge = FakeFindChatKnowledgeSummaryPort().apply {
            failure = IllegalStateException("chat knowledge db down")
        }

        command(
            publish = publish,
            findSummaries = summariesWith("summary one"),
            generation = generation,
            findUserKnowledge = findUserKnowledge,
            findChatKnowledge = findChatKnowledge,
        ).execute(FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message))

        val request = generation.requests.single()
        assertEquals(AUTHOR_SUMMARY, request.interests)
        assertNull(request.chatTopics)
        assertEquals(1, publish.executeCalls.size)
    }

    @Test
    fun `execute logs no knowledge values when a knowledge read fails`() {
        val message = requestMessage("что нового за 3 дня")
        val generation = RecordingNewsDigestGenerationPort(text = "llm digest")
        val findUserKnowledge = FakeFindUserKnowledgePort().apply {
            knowledge = mapOf(
                FakeVoProvider.createFromId(FROM_ID) to FakeModelProvider.createUserKnowledge(
                    facts = listOf(FakeModelProvider.createUserFact(value = AUTHOR_INTEREST)),
                    summary = AUTHOR_SUMMARY,
                    summaryFresh = true,
                ),
            )
        }
        val findChatKnowledge = FakeFindChatKnowledgeSummaryPort().apply {
            failure = IllegalStateException("chat knowledge db down")
        }

        SystemLoggerCapture(ProcessNewsDigestCommandImpl::class.java.name).use { capture ->
            command(
                findSummaries = summariesWith("summary one"),
                generation = generation,
                findUserKnowledge = findUserKnowledge,
                findChatKnowledge = findChatKnowledge,
            ).execute(FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message))

            val warning = capture.messages(Level.WARNING).single()
            assertTrue(warning.contains("IllegalStateException"))
            assertTrue(!warning.contains("футбол"))
            assertTrue(!warning.contains(AUTHOR_SUMMARY))
            assertTrue(!warning.contains(AUTHOR_INTEREST))
        }

        assertNull(generation.requests.single().chatTopics)
    }

    @ParameterizedTest
    @ValueSource(strings = ["author", "chat"])
    fun `execute restores the interrupt flag and rethrows InterruptedException`(failingRead: String) {
        val message = requestMessage("что нового за 3 дня")
        val publish = FakePublishEventCommand()
        val generation = RecordingNewsDigestGenerationPort(text = "llm digest")
        val digestCommand = if (failingRead == "author") {
            command(
                publish = publish,
                findSummaries = summariesWith("summary one"),
                generation = generation,
                findUserKnowledge = InterruptingFindUserKnowledgePort(),
            )
        } else {
            command(
                publish = publish,
                findSummaries = summariesWith("summary one"),
                generation = generation,
                findChatKnowledge = InterruptingFindChatKnowledgeSummaryPort(),
            )
        }
        val loggerCapture = SystemLoggerCapture(ProcessNewsDigestCommandImpl::class.java.name)

        try {
            assertThrows(InterruptedException::class.java) {
                digestCommand.execute(FakeNewsDigestProvider.createProcessNewsDigestCommandRequest(message))
            }

            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(generation.requests.isEmpty())
            assertTrue(publish.executeCalls.isEmpty())
            assertEquals(emptyList<String>(), loggerCapture.messages(Level.WARNING))
        } finally {
            Thread.interrupted()
            loggerCapture.close()
        }
    }

    private fun summariesWith(shortSummary: String) = RecordingFindStoredSummariesInPeriodPort(
        listOf(summary(firstMessageId = 10, lastMessageId = 25, shortSummary = shortSummary)),
    )

    private fun assertDataRequests(
        findSummaries: RecordingFindStoredSummariesInPeriodPort,
        findMessages: RecordingFindStoredMessagesBetweenDatesPort,
        findUserProfiles: RecordingFindUserProfilesByIdsPort,
        findGroupProfiles: RecordingFindGroupProfilesByIdsPort,
    ) {
        assertEquals(
            FakePersistencePortProvider.createFindStoredSummariesInPeriodRequest(PEER_ID, EXPLICIT_FROM, EXPLICIT_TO),
            findSummaries.requests.single(),
        )
        assertEquals(
            FakePersistencePortProvider.createFindStoredMessagesBetweenDatesRequest(
                peerId = PEER_ID,
                from = EXPLICIT_FROM,
                to = EXPLICIT_TO,
                limit = SUMMARY_BATCH_SIZE,
            ),
            findMessages.requests.single(),
        )
        assertEquals(
            FakePersistencePortProvider.createFindUserProfilesByIdsRequest(listOf(FakeVoProvider.createFromId(5))),
            findUserProfiles.requests.single(),
        )
        assertEquals(
            FakePersistencePortProvider.createFindGroupProfilesByIdsRequest(listOf(FakeVoProvider.createFromId(-8))),
            findGroupProfiles.requests.single(),
        )
    }

    private fun requestMessage(text: String): Message = FakeVoProvider.createMessage(
        date = FakeVoProvider.createDate(REQUEST_DATE),
        fromId = FakeVoProvider.createFromId(FROM_ID),
        peerId = FakeVoProvider.createPeerId(PEER_ID),
        conversationMessageId = FakeVoProvider.createConversationMessageId(REQUEST_MESSAGE_ID),
        messageText = FakeVoProvider.createMessageText(text),
    )

    private fun storedMessage(
        conversationMessageId: Long,
        date: OffsetDateTime = REQUEST_DATE,
        fromId: Long = 5,
        messageText: String = "hello",
    ) = FakeModelProvider.createStoredMessage(
        peerId = PEER_ID,
        conversationMessageId = conversationMessageId,
        fromId = fromId,
        messageText = messageText,
        date = date,
    )

    private fun summary(
        firstMessageId: Long,
        lastMessageId: Long?,
        shortSummary: String?,
    ): Summary = FakeModelProvider.createSummary(
        peerId = PEER_ID,
        firstMessageId = firstMessageId,
        lastMessageId = lastMessageId,
        shortSummary = shortSummary,
    )

    private fun assertAnswer(publish: FakePublishEventCommand, expectedText: String, message: Message) {
        val request = publish.executeCalls.single()
        assertEquals(Event.ANSWER_MESSAGE, request.event)
        assertEquals(
            FakeModelProvider.createResponseMessage(
                messageText = FakeVoProvider.createMessageText(expectedText),
                responseTo = message,
            ),
            request.payload.value,
        )
    }

    @Suppress("LongParameterList")
    private fun command(
        publish: FakePublishEventCommand = FakePublishEventCommand(),
        findLast: FindLastStoredMessageByAuthorPort = RecordingFindLastStoredMessageByAuthorPort(),
        findSummaries: FindStoredSummariesInPeriodPort = RecordingFindStoredSummariesInPeriodPort(),
        findMessages: FindStoredMessagesBetweenDatesPort = RecordingFindStoredMessagesBetweenDatesPort(),
        generation: NewsDigestGenerationPort = RecordingNewsDigestGenerationPort(),
        findUserProfiles: FindStoredUserProfilesByIdsPort = RecordingFindUserProfilesByIdsPort(),
        findGroupProfiles: FindStoredGroupProfilesByIdsPort = RecordingFindGroupProfilesByIdsPort(),
        findUserKnowledge: FindUserKnowledgePort = FakeFindUserKnowledgePort(),
        findChatKnowledge: FindChatKnowledgeSummaryPort = FakeFindChatKnowledgeSummaryPort(),
        personalizationEnabled: Boolean = true,
    ) = ProcessNewsDigestCommandImpl(
        publishEventCommand = publish,
        findLastStoredMessageByAuthorPort = findLast,
        findStoredSummariesInPeriodPort = findSummaries,
        findStoredMessagesBetweenDatesPort = findMessages,
        newsDigestGenerationPort = generation,
        messageAuthorFormatter = MessageAuthorFormatter(findUserProfiles, findGroupProfiles),
        summaryBatchSize = SUMMARY_BATCH_SIZE,
        findUserKnowledgePort = findUserKnowledge,
        findChatKnowledgeSummaryPort = findChatKnowledge,
        newsPersonalizationEnabled = personalizationEnabled,
    )

    private class RecordingFindLastStoredMessageByAuthorPort(
        private val message: StoredMessage? = null,
    ) : FindLastStoredMessageByAuthorPort {
        val requests = ConcurrentLinkedQueue<FindLastStoredMessageByAuthorPort.Request>()

        override fun execute(
            request: FindLastStoredMessageByAuthorPort.Request,
        ): FindLastStoredMessageByAuthorPort.Response {
            requests.add(request)
            return FakeNewsDigestProvider.createFindLastStoredMessageByAuthorResponse(message)
        }
    }

    private class RecordingFindStoredSummariesInPeriodPort(
        private val summaries: List<Summary> = emptyList(),
    ) : FindStoredSummariesInPeriodPort {
        val requests = ConcurrentLinkedQueue<FindStoredSummariesInPeriodPort.Request>()

        override fun execute(
            request: FindStoredSummariesInPeriodPort.Request,
        ): FindStoredSummariesInPeriodPort.Response {
            requests.add(request)
            return FakeNewsDigestProvider.createFindStoredSummariesInPeriodResponse(summaries)
        }
    }

    private class FailingFindStoredSummariesInPeriodPort(
        private val failure: RuntimeException,
    ) : FindStoredSummariesInPeriodPort {
        override fun execute(
            request: FindStoredSummariesInPeriodPort.Request,
        ): FindStoredSummariesInPeriodPort.Response = throw failure
    }

    private class RecordingFindStoredMessagesBetweenDatesPort(
        private val messages: List<StoredMessage> = emptyList(),
    ) : FindStoredMessagesBetweenDatesPort {
        val requests = ConcurrentLinkedQueue<FindStoredMessagesBetweenDatesPort.Request>()

        override fun execute(
            request: FindStoredMessagesBetweenDatesPort.Request,
        ): FindStoredMessagesBetweenDatesPort.Response {
            requests.add(request)
            return FakeNewsDigestProvider.createFindStoredMessagesBetweenDatesResponse(messages)
        }
    }

    private class RecordingNewsDigestGenerationPort(
        private val text: String = "digest text",
        private val failure: Exception? = null,
    ) : NewsDigestGenerationPort {
        val requests = ConcurrentLinkedQueue<NewsDigestGenerationRequest>()

        override fun execute(request: NewsDigestGenerationRequest): NewsDigestGenerationResponse {
            requests.add(request)
            failure?.let { throw it }
            return FakeNewsDigestProvider.createNewsDigestGenerationResponse(text)
        }
    }

    private class RecordingFindUserProfilesByIdsPort(
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

    private class RecordingFindGroupProfilesByIdsPort(
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

    private class InterruptingFindUserKnowledgePort : FindUserKnowledgePort {
        override fun execute(
            request: FindUserKnowledgePort.Request,
        ): FindUserKnowledgePort.Response = throw InterruptedException("interrupted")
    }

    private class InterruptingFindChatKnowledgeSummaryPort : FindChatKnowledgeSummaryPort {
        override fun execute(
            request: FindChatKnowledgeSummaryPort.Request,
        ): FindChatKnowledgeSummaryPort.Response = throw InterruptedException("interrupted")
    }

    companion object {

        @JvmStatic
        fun lastAuthorMessageCases(): Stream<Arguments> = Stream.of(
            Arguments.of(OffsetDateTime.parse("2026-11-08T09:00:00Z"), OffsetDateTime.parse("2026-11-08T09:00:00Z")),
            Arguments.of(null, WEEK_AGO),
            Arguments.of(OffsetDateTime.parse("2026-10-01T00:00:00Z"), WEEK_AGO),
            Arguments.of(OffsetDateTime.parse("2026-11-11T00:00:00Z"), WEEK_AGO),
        )
    }
}

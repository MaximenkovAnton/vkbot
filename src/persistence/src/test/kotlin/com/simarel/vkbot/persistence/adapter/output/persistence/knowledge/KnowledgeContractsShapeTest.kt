package com.simarel.vkbot.persistence.adapter.output.persistence.knowledge

import com.simarel.vkbot.share.domain.model.knowledge.ChatKnowledge
import com.simarel.vkbot.share.domain.model.knowledge.KnowledgeSourceKind
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCandidate
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import com.simarel.vkbot.share.domain.model.knowledge.UserFactEvidence
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import com.simarel.vkbot.share.domain.model.knowledge.UserKnowledge
import com.simarel.vkbot.share.port.output.OutputPort
import com.simarel.vkbot.share.port.output.persistence.AdvanceExtractionCursorPort
import com.simarel.vkbot.share.port.output.persistence.FindPendingKnowledgeBatchPort
import com.simarel.vkbot.share.port.output.persistence.HasPendingKnowledgeBatchPort
import com.simarel.vkbot.share.port.output.persistence.SaveChatKnowledgeSummaryPort
import com.simarel.vkbot.share.port.output.persistence.SaveUserFactsPort
import com.simarel.vkbot.share.port.output.persistence.SaveUserKnowledgeSummaryPort
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

private const val PEER_ID = 20L
private const val USER_ID = 10L
private const val CONVERSATION_MESSAGE_ID = 101L
private val FIXED_DATE: OffsetDateTime = OffsetDateTime.parse("2026-01-01T00:00:00Z")
private val FACT_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000010")
private val MESSAGE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000101")

class KnowledgeContractsShapeTest {

    @Test
    fun `constructsEveryContractThroughProviders`() {
        assertFactProvider()
        assertKnowledgeViewProviders()
        assertPersistencePortProviders()
    }

    @Test
    fun `declaresFullContractShape`() {
        assertFactShapes()
        assertKnowledgeViewShapes()
        assertPortRequestShapes()
        assertPortsAreOutputPorts()
        assertPortsExecuteThroughTheirContracts()
    }

    private fun assertFactProvider() {
        assertFactEntityProvider()
        assertEvidenceProvider()
        assertCandidateProvider()
    }

    private fun assertFactEntityProvider() {
        val fact = FakeModelProvider.createUserFact(
            id = FACT_ID,
            userId = USER_ID,
            peerId = PEER_ID,
            category = UserFactCategory.INTEREST,
            factKey = "interest_football",
            value = "футбол",
            valuePayload = mapOf("level" to "novice"),
            confidence = 0.9,
            status = UserFactStatus.CONFIRMED,
            evidenceCount = 1,
            firstObservedAt = FIXED_DATE,
            lastConfirmedAt = FIXED_DATE,
            supersededAt = null,
            conflict = false,
            stale = false,
        )
        assertEquals(FACT_ID, fact.id)
        assertEquals(UserFactStatus.CONFIRMED, fact.status)
        assertEquals(1, fact.evidenceCount)
        assertFalse(fact.conflict)
        assertFalse(fact.stale)
    }

    private fun assertEvidenceProvider() {
        val evidence = FakeModelProvider.createUserFactEvidence(
            id = FACT_ID,
            factId = FACT_ID,
            messageId = MESSAGE_ID,
            conversationMessageId = CONVERSATION_MESSAGE_ID,
            peerId = PEER_ID,
            saidById = USER_ID,
            sourceKind = KnowledgeSourceKind.SELF_STATEMENT,
            confidence = 0.9,
            observedAt = FIXED_DATE,
        )
        assertEquals(CONVERSATION_MESSAGE_ID, evidence.conversationMessageId)
        assertEquals(KnowledgeSourceKind.SELF_STATEMENT, evidence.sourceKind)
        assertEquals(0.9, evidence.confidence)
    }

    private fun assertCandidateProvider() {
        val candidate = FakeModelProvider.createUserFactCandidate(
            userId = USER_ID,
            peerId = PEER_ID,
            category = UserFactCategory.INTEREST,
            factKey = "interest_football",
            value = "футбол",
            valuePayload = emptyMap(),
            sourceKind = KnowledgeSourceKind.SELF_STATEMENT,
            confidence = 0.9,
            messageId = MESSAGE_ID,
            conversationMessageId = CONVERSATION_MESSAGE_ID,
            saidById = USER_ID,
            observedAt = FIXED_DATE,
        )
        assertEquals(USER_ID, candidate.userId)
        assertEquals(PEER_ID, candidate.peerId)
        assertEquals(UserFactCategory.INTEREST, candidate.category)
        assertEquals("interest_football", candidate.factKey)
        assertEquals("футбол", candidate.value)
        assertEquals(KnowledgeSourceKind.SELF_STATEMENT, candidate.sourceKind)
        assertEquals(0.9, candidate.confidence)
        assertEquals(CONVERSATION_MESSAGE_ID, candidate.conversationMessageId)
        assertEquals(FIXED_DATE, candidate.observedAt)
    }

    private fun assertKnowledgeViewProviders() {
        val knowledge = FakeModelProvider.createUserKnowledge(
            facts = emptyList(),
            summary = null,
            summaryFresh = false,
        )
        assertTrue(knowledge.facts.isEmpty())
        assertNull(knowledge.summary)
        assertFalse(knowledge.summaryFresh)

        val chatKnowledge = FakeModelProvider.createChatKnowledge(
            peerId = PEER_ID,
            summary = "chat summary",
            summarizedAt = FIXED_DATE,
        )
        assertEquals(PEER_ID, chatKnowledge.peerId)
        assertEquals("chat summary", chatKnowledge.summary)
    }

    private fun assertPersistencePortProviders() {
        val hasPendingRequest = FakePersistencePortProvider.createHasPendingKnowledgeBatchRequest(
            peerId = PEER_ID,
            minCount = 25,
            minInterval = Duration.ofMinutes(30),
        )
        assertEquals(25, hasPendingRequest.minCount)
        assertEquals(Duration.ofMinutes(30), hasPendingRequest.minInterval)

        val findRequest = FakePersistencePortProvider.createFindPendingKnowledgeBatchRequest(
            peerId = PEER_ID,
            upToConversationMessageId = 30,
            limit = 25,
        )
        assertEquals(30, findRequest.upToConversationMessageId)
        assertEquals(25, findRequest.limit)

        assertEquals(1, FakePersistencePortProvider.createSaveUserFactsRequest().candidates.size)
        assertEquals(
            30,
            FakePersistencePortProvider.createAdvanceExtractionCursorRequest().lastConversationMessageId,
        )
        assertEquals(10, FakePersistencePortProvider.createSaveUserKnowledgeSummaryRequest().userId)
        assertEquals(7, FakePersistencePortProvider.createSaveChatKnowledgeSummaryRequest().messagesCovered)

        assertFalse(FakePersistencePortProvider.createHasPendingKnowledgeBatchResponse().hasPending)
        assertTrue(FakePersistencePortProvider.createFindPendingKnowledgeBatchResponse().messages.isEmpty())
        assertEquals(1, FakePersistencePortProvider.createSaveUserFactsResponse().saved)
        FakePersistencePortProvider.createAdvanceExtractionCursorResponse()
        FakePersistencePortProvider.createSaveUserKnowledgeSummaryResponse()
        FakePersistencePortProvider.createSaveChatKnowledgeSummaryResponse()
    }

    private fun assertFactShapes() {
        assertShape(
            com.simarel.vkbot.share.domain.model.knowledge.UserFact::class,
            "id: java.util.UUID",
            "userId: kotlin.Long",
            "peerId: kotlin.Long",
            "category: com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory",
            "factKey: kotlin.String",
            "value: kotlin.String",
            "valuePayload: kotlin.collections.Map<kotlin.String, kotlin.Any?>",
            "confidence: kotlin.Double",
            "status: com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus",
            "evidenceCount: kotlin.Int",
            "firstObservedAt: java.time.OffsetDateTime",
            "lastConfirmedAt: java.time.OffsetDateTime",
            "supersededAt: java.time.OffsetDateTime?",
            "conflict: kotlin.Boolean",
            "stale: kotlin.Boolean",
        )
        assertShape(
            UserFactEvidence::class,
            "id: java.util.UUID",
            "factId: java.util.UUID",
            "messageId: java.util.UUID",
            "conversationMessageId: kotlin.Long",
            "peerId: kotlin.Long",
            "saidById: kotlin.Long",
            "sourceKind: com.simarel.vkbot.share.domain.model.knowledge.KnowledgeSourceKind",
            "confidence: kotlin.Double",
            "observedAt: java.time.OffsetDateTime",
        )
        assertShape(
            UserFactCandidate::class,
            "userId: kotlin.Long",
            "peerId: kotlin.Long",
            "category: com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory",
            "factKey: kotlin.String",
            "value: kotlin.String",
            "valuePayload: kotlin.collections.Map<kotlin.String, kotlin.Any?>",
            "sourceKind: com.simarel.vkbot.share.domain.model.knowledge.KnowledgeSourceKind",
            "confidence: kotlin.Double",
            "messageId: java.util.UUID",
            "conversationMessageId: kotlin.Long",
            "saidById: kotlin.Long",
            "observedAt: java.time.OffsetDateTime",
        )
    }

    private fun assertKnowledgeViewShapes() {
        assertShape(
            UserKnowledge::class,
            "facts: kotlin.collections.List<com.simarel.vkbot.share.domain.model.knowledge.UserFact>",
            "summary: kotlin.String?",
            "summaryFresh: kotlin.Boolean",
        )
        assertShape(
            ChatKnowledge::class,
            "peerId: kotlin.Long",
            "summary: kotlin.String",
            "summarizedAt: java.time.OffsetDateTime",
        )
    }

    private fun assertPortRequestShapes() {
        assertShape(
            HasPendingKnowledgeBatchPort.Request::class,
            "peerId: kotlin.Long",
            "minCount: kotlin.Int",
            "minInterval: java.time.Duration",
        )
        assertShape(HasPendingKnowledgeBatchPort.Response::class, "hasPending: kotlin.Boolean")
        assertShape(
            FindPendingKnowledgeBatchPort.Request::class,
            "peerId: kotlin.Long",
            "upToConversationMessageId: kotlin.Long",
            "limit: kotlin.Int",
        )
        assertShape(
            FindPendingKnowledgeBatchPort.Response::class,
            "messages: kotlin.collections.List<com.simarel.vkbot.share.domain.model.StoredMessage>",
        )
        assertShape(
            SaveUserFactsPort.Request::class,
            "candidates: kotlin.collections.List<com.simarel.vkbot.share.domain.model.knowledge.UserFactCandidate>",
        )
        assertShape(SaveUserFactsPort.Response::class, "saved: kotlin.Int", "skipped: kotlin.Int")
        assertShape(
            AdvanceExtractionCursorPort.Request::class,
            "peerId: kotlin.Long",
            "lastConversationMessageId: kotlin.Long",
            "completedAt: java.time.OffsetDateTime",
        )
        assertShape(AdvanceExtractionCursorPort.Response::class)
        assertShape(SaveUserKnowledgeSummaryPort.Request::class, "userId: kotlin.Long", "summaryText: kotlin.String")
        assertShape(SaveUserKnowledgeSummaryPort.Response::class)
        assertShape(
            SaveChatKnowledgeSummaryPort.Request::class,
            "peerId: kotlin.Long",
            "summaryText: kotlin.String",
            "firstConversationMessageId: kotlin.Long",
            "lastConversationMessageId: kotlin.Long",
            "messagesCovered: kotlin.Int",
        )
        assertShape(SaveChatKnowledgeSummaryPort.Response::class)
    }

    private fun assertPortsAreOutputPorts() {
        val ports: List<KClass<*>> = listOf(
            HasPendingKnowledgeBatchPort::class,
            FindPendingKnowledgeBatchPort::class,
            SaveUserFactsPort::class,
            AdvanceExtractionCursorPort::class,
            SaveUserKnowledgeSummaryPort::class,
            SaveChatKnowledgeSummaryPort::class,
        )
        ports.forEach { port ->
            assertTrue(port.java.isInterface, port.qualifiedName)
            assertTrue(OutputPort::class.java.isAssignableFrom(port.java), port.qualifiedName)
        }
    }

    private fun assertPortsExecuteThroughTheirContracts() {
        val hasPendingResponse = FakePersistencePortProvider.createHasPendingKnowledgeBatchResponse()
        val hasPendingPort = object : HasPendingKnowledgeBatchPort {
            override fun execute(
                request: HasPendingKnowledgeBatchPort.Request,
            ): HasPendingKnowledgeBatchPort.Response = hasPendingResponse
        }
        assertFalse(
            executeHasPending(
                hasPendingPort,
                FakePersistencePortProvider.createHasPendingKnowledgeBatchRequest(),
            ).hasPending,
        )
        val findResponse = FakePersistencePortProvider.createFindPendingKnowledgeBatchResponse()
        val findPort = object : FindPendingKnowledgeBatchPort {
            override fun execute(
                request: FindPendingKnowledgeBatchPort.Request,
            ): FindPendingKnowledgeBatchPort.Response = findResponse
        }
        assertTrue(
            executeFindPending(
                findPort,
                FakePersistencePortProvider.createFindPendingKnowledgeBatchRequest(),
            ).messages.isEmpty(),
        )
        val saveFactsResponse = FakePersistencePortProvider.createSaveUserFactsResponse()
        val saveFactsPort = object : SaveUserFactsPort {
            override fun execute(
                request: SaveUserFactsPort.Request,
            ): SaveUserFactsPort.Response = saveFactsResponse
        }
        assertEquals(
            1,
            executeSaveUserFacts(saveFactsPort, FakePersistencePortProvider.createSaveUserFactsRequest()).saved,
        )
        val advanceResponse = FakePersistencePortProvider.createAdvanceExtractionCursorResponse()
        val advancePort = object : AdvanceExtractionCursorPort {
            override fun execute(
                request: AdvanceExtractionCursorPort.Request,
            ): AdvanceExtractionCursorPort.Response = advanceResponse
        }
        executeAdvanceCursor(advancePort, FakePersistencePortProvider.createAdvanceExtractionCursorRequest())
        val saveUserSummaryResponse = FakePersistencePortProvider.createSaveUserKnowledgeSummaryResponse()
        val saveUserSummaryPort = object : SaveUserKnowledgeSummaryPort {
            override fun execute(
                request: SaveUserKnowledgeSummaryPort.Request,
            ): SaveUserKnowledgeSummaryPort.Response = saveUserSummaryResponse
        }
        executeSaveUserSummary(
            saveUserSummaryPort,
            FakePersistencePortProvider.createSaveUserKnowledgeSummaryRequest(),
        )
        val saveChatSummaryResponse = FakePersistencePortProvider.createSaveChatKnowledgeSummaryResponse()
        val saveChatSummaryPort = object : SaveChatKnowledgeSummaryPort {
            override fun execute(
                request: SaveChatKnowledgeSummaryPort.Request,
            ): SaveChatKnowledgeSummaryPort.Response = saveChatSummaryResponse
        }
        executeSaveChatSummary(
            saveChatSummaryPort,
            FakePersistencePortProvider.createSaveChatKnowledgeSummaryRequest(),
        )
    }

    private fun assertShape(kClass: KClass<*>, vararg expected: String) {
        val actual = kClass.primaryConstructor?.parameters.orEmpty().map { "${it.name}: ${describeType(it.type)}" }
        assertEquals(expected.toList(), actual, kClass.qualifiedName)
    }

    private fun describeType(type: KType): String = buildString {
        append((type.classifier as? KClass<*>)?.qualifiedName)
        if (type.arguments.isNotEmpty()) {
            append(type.arguments.joinToString(prefix = "<", postfix = ">") { describeType(it.type!!) })
        }
        if (type.isMarkedNullable) append("?")
    }

    private fun executeHasPending(
        port: HasPendingKnowledgeBatchPort,
        request: HasPendingKnowledgeBatchPort.Request,
    ): HasPendingKnowledgeBatchPort.Response = port.execute(request)

    private fun executeFindPending(
        port: FindPendingKnowledgeBatchPort,
        request: FindPendingKnowledgeBatchPort.Request,
    ): FindPendingKnowledgeBatchPort.Response = port.execute(request)

    private fun executeSaveUserFacts(
        port: SaveUserFactsPort,
        request: SaveUserFactsPort.Request,
    ): SaveUserFactsPort.Response = port.execute(request)

    private fun executeAdvanceCursor(
        port: AdvanceExtractionCursorPort,
        request: AdvanceExtractionCursorPort.Request,
    ): AdvanceExtractionCursorPort.Response = port.execute(request)

    private fun executeSaveUserSummary(
        port: SaveUserKnowledgeSummaryPort,
        request: SaveUserKnowledgeSummaryPort.Request,
    ): SaveUserKnowledgeSummaryPort.Response = port.execute(request)

    private fun executeSaveChatSummary(
        port: SaveChatKnowledgeSummaryPort,
        request: SaveChatKnowledgeSummaryPort.Request,
    ): SaveChatKnowledgeSummaryPort.Response = port.execute(request)
}

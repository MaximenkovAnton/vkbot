package com.simarel.vkbot.integration

import com.simarel.vkbot.persistence.domain.knowledge.KnowledgeValueHash
import com.simarel.vkbot.share.domain.model.knowledge.KnowledgeSourceKind
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCandidate
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import com.simarel.vkbot.share.port.output.persistence.AdvanceExtractionCursorPort
import com.simarel.vkbot.share.port.output.persistence.FindPendingKnowledgeBatchPort
import com.simarel.vkbot.share.port.output.persistence.HasPendingKnowledgeBatchPort
import com.simarel.vkbot.share.port.output.persistence.SaveChatKnowledgeSummaryPort
import com.simarel.vkbot.share.port.output.persistence.SaveUserFactsPort
import com.simarel.vkbot.share.port.output.persistence.SaveUserKnowledgeSummaryPort
import com.simarel.vkbot.testfixtures.domain.FakeModelProvider
import com.simarel.vkbot.testfixtures.port.FakePersistencePortProvider
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Types
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

class UserKnowledgePersistenceFixtures(
    private val dataSource: DataSource,
    private val saveUserFactsPort: SaveUserFactsPort,
    private val hasPendingKnowledgeBatchPort: HasPendingKnowledgeBatchPort,
    private val findPendingKnowledgeBatchPort: FindPendingKnowledgeBatchPort,
    private val advanceExtractionCursorPort: AdvanceExtractionCursorPort,
    private val saveUserKnowledgeSummaryPort: SaveUserKnowledgeSummaryPort,
    private val saveChatKnowledgeSummaryPort: SaveChatKnowledgeSummaryPort,
) {

    private val messageIds = mutableMapOf<Long, UUID>()

    fun insertMessage(
        peerId: Long,
        conversationMessageId: Long,
        fromId: Long,
        text: String,
        date: OffsetDateTime,
    ): UUID {
        val messageId = UUID.randomUUID()
        execute(
            "insert into messages " +
                "(id, date, from_id, group_id, peer_id, conversation_message_id, message_text) " +
                "values (?, ?, ?, ?, ?, ?, ?)",
            messageId,
            date,
            fromId,
            GROUP_ID,
            peerId,
            conversationMessageId,
            text,
        )
        messageIds[conversationMessageId] = messageId
        return messageId
    }

    fun insertCursor(peerId: Long, lastConversationMessageId: Long, lastCompletedAt: OffsetDateTime) {
        execute(
            "insert into knowledge_extraction_cursors " +
                "(peer_id, last_conversation_message_id, last_completed_at, updated_at) values (?, ?, ?, ?)",
            peerId,
            lastConversationMessageId,
            lastCompletedAt,
            lastCompletedAt,
        )
    }

    fun insertFact(
        userId: Long,
        value: String,
        status: UserFactStatus,
        lastConfirmedAt: OffsetDateTime,
    ) {
        execute(
            "insert into user_facts " +
                "(id, user_id, peer_id, category, fact_key, value, value_hash, evidence_count, " +
                "max_evidence_confidence, confidence, status, first_observed_at, last_confirmed_at, " +
                "conflict, created_at, updated_at) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())",
            UUID.randomUUID(),
            userId,
            userId,
            UserFactCategory.INTEREST.name,
            "interest_summary",
            value,
            KnowledgeValueHash.of(value),
            FACTS_INITIAL,
            BigDecimal("0.9"),
            BigDecimal("0.9"),
            status.name,
            lastConfirmedAt,
            lastConfirmedAt,
            false,
        )
    }

    @Suppress("LongParameterList")
    fun candidate(
        userId: Long,
        peerId: Long,
        category: UserFactCategory,
        factKey: String,
        value: String,
        sourceKind: KnowledgeSourceKind,
        confidence: Double,
        conversationMessageId: Long,
        observedAt: OffsetDateTime,
    ): UserFactCandidate = FakeModelProvider.createUserFactCandidate(
        userId = userId,
        peerId = peerId,
        category = category,
        factKey = factKey,
        value = value,
        sourceKind = sourceKind,
        confidence = confidence,
        messageId = messageIds[conversationMessageId] ?: UUID.randomUUID(),
        conversationMessageId = conversationMessageId,
        observedAt = observedAt,
    )

    fun save(candidates: List<UserFactCandidate>): SaveUserFactsPort.Response {
        val request = FakePersistencePortProvider.createSaveUserFactsRequest(candidates = candidates)
        return saveUserFactsPort.execute(request)
    }

    fun saveSingle(candidate: UserFactCandidate): SaveUserFactsPort.Response = save(listOf(candidate))

    fun pendingIds(peerId: Long, upToConversationMessageId: Long, limit: Int): List<Long> {
        val request = FakePersistencePortProvider.createFindPendingKnowledgeBatchRequest(
            peerId = peerId,
            upToConversationMessageId = upToConversationMessageId,
            limit = limit,
        )
        return findPendingKnowledgeBatchPort.execute(request).messages.map { it.conversationMessageId }
    }

    fun hasPending(peerId: Long, minCount: Int, minInterval: Duration = Duration.ofMinutes(30)): Boolean {
        val request = FakePersistencePortProvider.createHasPendingKnowledgeBatchRequest(
            peerId = peerId,
            minCount = minCount,
            minInterval = minInterval,
        )
        return hasPendingKnowledgeBatchPort.execute(request).hasPending
    }

    fun advance(peerId: Long, lastConversationMessageId: Long, completedAt: String) {
        advanceExtractionCursorPort.execute(
            FakePersistencePortProvider.createAdvanceExtractionCursorRequest(
                peerId = peerId,
                lastConversationMessageId = lastConversationMessageId,
                completedAt = OffsetDateTime.parse(completedAt),
            ),
        )
    }

    fun cursor(peerId: Long): Map<String, Any?> {
        val cursorRows = rows("select * from knowledge_extraction_cursors where peer_id = ?", peerId)
        return cursorRows.single()
    }

    fun saveUserSummary(userId: Long, summaryText: String) {
        saveUserKnowledgeSummaryPort.execute(
            FakePersistencePortProvider.createSaveUserKnowledgeSummaryRequest(
                userId = userId,
                summaryText = summaryText,
            ),
        )
    }

    fun saveChatSummary(
        peerId: Long,
        summaryText: String,
        firstConversationMessageId: Long,
        lastConversationMessageId: Long,
        messagesCovered: Int,
    ) {
        saveChatKnowledgeSummaryPort.execute(
            FakePersistencePortProvider.createSaveChatKnowledgeSummaryRequest(
                peerId = peerId,
                summaryText = summaryText,
                firstConversationMessageId = firstConversationMessageId,
                lastConversationMessageId = lastConversationMessageId,
                messagesCovered = messagesCovered,
            ),
        )
    }

    fun facts(userId: Long): List<Map<String, Any?>> = rows(
        "select * from user_facts where user_id = ?",
        userId,
    )

    fun fact(userId: Long, value: String): Map<String, Any?> = facts(userId).single { it["value"] == value }

    fun evidence(userId: Long): List<Map<String, Any?>> = rows(
        "select e.* from user_fact_evidence e join user_facts f on f.id = e.fact_id where f.user_id = ?",
        userId,
    )

    fun userSummary(userId: Long): Map<String, Any?> = rows(
        "select * from user_knowledge_summary where user_id = ?",
        userId,
    ).single()

    fun chatSummary(peerId: Long): Map<String, Any?> = rows(
        "select * from chat_knowledge_summary where peer_id = ?",
        peerId,
    ).single()

    fun tableTextOccurrences(table: String, marker: String): Int {
        val row = rows("select count(*) as total from $table t where t::text like ?", "%$marker%").single()
        return (row.getValue("total") as Number).toInt()
    }

    fun count(sql: String, vararg params: Any?): Int = (rows(sql, *params).single().getValue("total") as Number).toInt()

    fun rows(sql: String, vararg params: Any?): List<Map<String, Any?>> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, param -> statement.setObject(index + 1, param) }
                statement.executeQuery().use { resultSet -> return readRows(resultSet) }
            }
        }
    }

    fun execute(sql: String, vararg params: Any?) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, param -> statement.setObject(index + 1, param) }
                statement.executeUpdate()
            }
        }
    }

    private fun readRows(resultSet: ResultSet): List<Map<String, Any?>> {
        val metadata = resultSet.metaData
        val rows = mutableListOf<Map<String, Any?>>()
        while (resultSet.next()) {
            rows += (1..metadata.columnCount).associate { index ->
                metadata.getColumnLabel(index) to readValue(resultSet, metadata.getColumnType(index), index)
            }
        }
        return rows
    }

    private fun readValue(resultSet: ResultSet, sqlType: Int, index: Int): Any? = when (sqlType) {
        Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> resultSet.getObject(index, OffsetDateTime::class.java)
        else -> resultSet.getObject(index)
    }

    private companion object {
        private const val GROUP_ID = 0L
        private const val FACTS_INITIAL = 1
    }
}

fun Map<String, Any?>.asString(column: String): String = getValue(column) as String

fun Map<String, Any?>.asLong(column: String): Long = (getValue(column) as Number).toLong()

fun Map<String, Any?>.asInt(column: String): Int = (getValue(column) as Number).toInt()

fun Map<String, Any?>.asDouble(column: String): Double = (getValue(column) as Number).toDouble()

fun Map<String, Any?>.asBoolean(column: String): Boolean = getValue(column) as Boolean

fun Map<String, Any?>.asTimestamp(column: String): OffsetDateTime = getValue(column) as OffsetDateTime

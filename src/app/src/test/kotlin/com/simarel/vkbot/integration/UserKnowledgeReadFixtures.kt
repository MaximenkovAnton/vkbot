package com.simarel.vkbot.integration

import com.simarel.vkbot.persistence.domain.knowledge.KnowledgeValueHash
import com.simarel.vkbot.share.domain.model.knowledge.UserFactCategory
import com.simarel.vkbot.share.domain.model.knowledge.UserFactStatus
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Types
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

class UserKnowledgeReadFixtures(
    private val dataSource: DataSource,
) {

    @Suppress("LongParameterList")
    fun insertFact(
        userId: Long,
        value: String,
        lastConfirmedAt: OffsetDateTime,
        factKey: String = "interest_sport",
        category: UserFactCategory = UserFactCategory.INTEREST,
        status: UserFactStatus = UserFactStatus.CONFIRMED,
        confidence: Double = 0.9,
        supersededAt: OffsetDateTime? = null,
        conflict: Boolean = false,
        peerId: Long = userId,
    ): UUID {
        val factId = UUID.randomUUID()
        execute(
            INSERT_FACT_SQL,
            factId,
            userId,
            peerId,
            category.name,
            factKey,
            value,
            KnowledgeValueHash.of(value),
            INITIAL_EVIDENCE_COUNT,
            BigDecimal.valueOf(confidence),
            BigDecimal.valueOf(confidence),
            status.name,
            lastConfirmedAt,
            lastConfirmedAt,
            supersededAt,
            conflict,
        )
        return factId
    }

    fun insertEvidence(
        factId: UUID,
        peerId: Long,
        conversationMessageId: Long = DEFAULT_CONVERSATION_MESSAGE_ID,
        observedAt: OffsetDateTime = OffsetDateTime.now(),
        confidence: Double = 0.9,
    ) {
        execute(
            INSERT_EVIDENCE_SQL,
            UUID.randomUUID(),
            factId,
            UUID.randomUUID(),
            conversationMessageId,
            peerId,
            peerId,
            DEFAULT_SOURCE_KIND,
            BigDecimal.valueOf(confidence),
            observedAt,
        )
    }

    fun insertSummary(
        userId: Long,
        summaryText: String,
        lastFactAt: OffsetDateTime,
        summarizedAt: OffsetDateTime,
    ) {
        execute(INSERT_SUMMARY_SQL, userId, summaryText, 1, lastFactAt, summarizedAt)
    }

    fun summaryRow(userId: Long): Map<String, Any?> = rows(SELECT_SUMMARY_ROW_SQL, userId).single()

    fun snapshot(userId: Long, peerId: Long): Map<String, String?> = mapOf(
        "user_facts" to hash(SNAPSHOT_FACTS_SQL, userId),
        "user_fact_evidence" to hash(SNAPSHOT_EVIDENCE_SQL, userId),
        "user_knowledge_summary" to hash(SNAPSHOT_USER_SUMMARY_SQL, userId),
        "chat_knowledge_summary" to hash(SNAPSHOT_CHAT_SUMMARY_SQL, peerId),
        "knowledge_extraction_cursors" to hash(SNAPSHOT_CURSORS_SQL, peerId),
    )

    fun counts(userId: Long, peerId: Long): Map<String, Int> = mapOf(
        "user_facts" to count(COUNT_FACTS_SQL, userId),
        "user_fact_evidence" to count(COUNT_EVIDENCE_SQL, userId),
        "user_knowledge_summary" to count(COUNT_USER_SUMMARY_SQL, userId),
        "chat_knowledge_summary" to count(COUNT_CHAT_SUMMARY_SQL, peerId),
        "knowledge_extraction_cursors" to count(COUNT_CURSORS_SQL, peerId),
    )

    fun deleteKnowledge(userId: Long, peerId: Long) {
        execute(DELETE_EVIDENCE_SQL, userId)
        execute(DELETE_FACTS_SQL, userId)
        execute(DELETE_USER_SUMMARY_SQL, userId)
        execute(DELETE_CHAT_SUMMARY_SQL, peerId)
        execute(DELETE_CURSORS_SQL, peerId)
    }

    private fun count(sql: String, vararg p: Any?): Int = (column(sql, "total", *p) as Number).toInt()

    private fun hash(sql: String, vararg p: Any?): String? = column(sql, "hash", *p) as String?

    private fun column(sql: String, column: String, vararg p: Any?): Any? = rows(sql, *p).single().getValue(column)

    private fun rows(sql: String, vararg params: Any?): List<Map<String, Any?>> {
        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                params.forEachIndexed { index, param -> statement.setObject(index + 1, param) }
                statement.executeQuery().use { resultSet -> return readRows(resultSet) }
            }
        }
    }

    private fun execute(sql: String, vararg params: Any?) {
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
                val column = metadata.getColumnLabel(index)
                val value = when (metadata.getColumnType(index)) {
                    Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE ->
                        resultSet.getObject(index, OffsetDateTime::class.java)

                    else -> resultSet.getObject(index)
                }
                column to value
            }
        }
        return rows
    }

    private companion object {
        private const val INITIAL_EVIDENCE_COUNT = 1
        private const val DEFAULT_CONVERSATION_MESSAGE_ID = 1L
        private const val DEFAULT_SOURCE_KIND = "SELF_STATEMENT"

        private const val INSERT_FACT_SQL =
            "insert into user_facts " +
                "(id, user_id, peer_id, category, fact_key, value, value_hash, evidence_count, " +
                "max_evidence_confidence, confidence, status, first_observed_at, last_confirmed_at, " +
                "superseded_at, conflict, created_at, updated_at) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())"

        private const val INSERT_EVIDENCE_SQL =
            "insert into user_fact_evidence " +
                "(id, fact_id, message_id, conversation_message_id, peer_id, said_by_id, source_kind, " +
                "confidence, observed_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)"

        private const val INSERT_SUMMARY_SQL =
            "insert into user_knowledge_summary " +
                "(user_id, summary_text, facts_covered, last_fact_at, summarized_at) values (?, ?, ?, ?, ?)"

        private const val SELECT_SUMMARY_ROW_SQL =
            "select summary_text, facts_covered, last_fact_at, summarized_at " +
                "from user_knowledge_summary where user_id = ?"

        private const val SNAPSHOT_FACTS_SQL =
            "select md5(string_agg(t::text, '|' order by t::text)) as hash from user_facts t " +
                "where t.user_id = ?"

        private const val SNAPSHOT_EVIDENCE_SQL =
            "select md5(string_agg(t::text, '|' order by t::text)) as hash from user_fact_evidence t " +
                "join user_facts f on f.id = t.fact_id where f.user_id = ?"

        private const val SNAPSHOT_USER_SUMMARY_SQL =
            "select md5(string_agg(t::text, '|' order by t::text)) as hash from user_knowledge_summary t " +
                "where t.user_id = ?"

        private const val SNAPSHOT_CHAT_SUMMARY_SQL =
            "select md5(string_agg(t::text, '|' order by t::text)) as hash from chat_knowledge_summary t " +
                "where t.peer_id = ?"

        private const val SNAPSHOT_CURSORS_SQL =
            "select md5(string_agg(t::text, '|' order by t::text)) as hash from knowledge_extraction_cursors t " +
                "where t.peer_id = ?"

        private const val COUNT_FACTS_SQL = "select count(*) as total from user_facts where user_id = ?"

        private const val COUNT_EVIDENCE_SQL =
            "select count(*) as total from user_fact_evidence e " +
                "join user_facts f on f.id = e.fact_id where f.user_id = ?"

        private const val COUNT_USER_SUMMARY_SQL =
            "select count(*) as total from user_knowledge_summary where user_id = ?"

        private const val COUNT_CHAT_SUMMARY_SQL =
            "select count(*) as total from chat_knowledge_summary where peer_id = ?"

        private const val COUNT_CURSORS_SQL =
            "select count(*) as total from knowledge_extraction_cursors where peer_id = ?"

        private const val DELETE_EVIDENCE_SQL =
            "delete from user_fact_evidence where fact_id in (select id from user_facts where user_id = ?)"

        private const val DELETE_FACTS_SQL = "delete from user_facts where user_id = ?"
        private const val DELETE_USER_SUMMARY_SQL = "delete from user_knowledge_summary where user_id = ?"
        private const val DELETE_CHAT_SUMMARY_SQL = "delete from chat_knowledge_summary where peer_id = ?"
        private const val DELETE_CURSORS_SQL = "delete from knowledge_extraction_cursors where peer_id = ?"
    }
}

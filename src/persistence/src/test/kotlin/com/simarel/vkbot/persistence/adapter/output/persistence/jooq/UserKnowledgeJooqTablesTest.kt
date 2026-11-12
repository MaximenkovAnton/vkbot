package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import org.jooq.DataType
import org.jooq.Field
import org.jooq.impl.SQLDataType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberProperties

class UserKnowledgeJooqTablesTest {

    @Test
    fun `exposesKnowledgeTableConstants`() {
        assertTableNames()
        assertUserFactsFields()
        assertEvidenceAndSummaryFields()
        assertCursorFields()
        assertOnlyExpectedConstants()
    }

    private fun assertTableNames() {
        assertEquals("user_facts", Tables.UserFacts.TABLE.name)
        assertEquals("user_fact_evidence", Tables.UserFactEvidence.TABLE.name)
        assertEquals("user_knowledge_summary", Tables.UserKnowledgeSummaries.TABLE.name)
        assertEquals("chat_knowledge_summary", Tables.ChatKnowledgeSummaries.TABLE.name)
        assertEquals("knowledge_extraction_cursors", Tables.KnowledgeExtractionCursors.TABLE.name)
    }

    private fun assertUserFactsFields() {
        assertField(Tables.UserFacts.ID, "id", SQLDataType.UUID)
        assertField(Tables.UserFacts.USER_ID, "user_id", SQLDataType.BIGINT)
        assertField(Tables.UserFacts.PEER_ID, "peer_id", SQLDataType.BIGINT)
        assertField(Tables.UserFacts.CATEGORY, "category", SQLDataType.OTHER)
        assertField(Tables.UserFacts.FACT_KEY, "fact_key", SQLDataType.OTHER)
        assertField(Tables.UserFacts.VALUE, "value", SQLDataType.OTHER)
        assertField(Tables.UserFacts.VALUE_HASH, "value_hash", SQLDataType.OTHER)
        assertField(Tables.UserFacts.VALUE_PAYLOAD, "value_payload", SQLDataType.JSONB)
        assertField(Tables.UserFacts.EVIDENCE_COUNT, "evidence_count", SQLDataType.INTEGER)
        assertField(Tables.UserFacts.MAX_EVIDENCE_CONFIDENCE, "max_evidence_confidence", SQLDataType.NUMERIC)
        assertField(Tables.UserFacts.CONFIDENCE, "confidence", SQLDataType.NUMERIC)
        assertField(Tables.UserFacts.STATUS, "status", SQLDataType.OTHER)
        assertField(Tables.UserFacts.FIRST_OBSERVED_AT, "first_observed_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
        assertField(Tables.UserFacts.LAST_CONFIRMED_AT, "last_confirmed_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
        assertField(Tables.UserFacts.SUPERSEDED_AT, "superseded_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
        assertField(Tables.UserFacts.CONFLICT, "conflict", SQLDataType.BOOLEAN)
        assertField(Tables.UserFacts.CREATED_AT, "created_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
        assertField(Tables.UserFacts.UPDATED_AT, "updated_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
    }

    private fun assertEvidenceAndSummaryFields() {
        assertField(Tables.UserFactEvidence.ID, "id", SQLDataType.UUID)
        assertField(Tables.UserFactEvidence.FACT_ID, "fact_id", SQLDataType.UUID)
        assertField(Tables.UserFactEvidence.MESSAGE_ID, "message_id", SQLDataType.UUID)
        assertField(Tables.UserFactEvidence.CONVERSATION_MESSAGE_ID, "conversation_message_id", SQLDataType.BIGINT)
        assertField(Tables.UserFactEvidence.PEER_ID, "peer_id", SQLDataType.BIGINT)
        assertField(Tables.UserFactEvidence.SAID_BY_ID, "said_by_id", SQLDataType.BIGINT)
        assertField(Tables.UserFactEvidence.SOURCE_KIND, "source_kind", SQLDataType.OTHER)
        assertField(Tables.UserFactEvidence.CONFIDENCE, "confidence", SQLDataType.NUMERIC)
        assertField(Tables.UserFactEvidence.OBSERVED_AT, "observed_at", SQLDataType.TIMESTAMPWITHTIMEZONE)

        assertField(Tables.UserKnowledgeSummaries.USER_ID, "user_id", SQLDataType.BIGINT)
        assertField(Tables.UserKnowledgeSummaries.SUMMARY_TEXT, "summary_text", SQLDataType.OTHER)
        assertField(Tables.UserKnowledgeSummaries.FACTS_COVERED, "facts_covered", SQLDataType.INTEGER)
        assertField(Tables.UserKnowledgeSummaries.LAST_FACT_AT, "last_fact_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
        assertField(Tables.UserKnowledgeSummaries.SUMMARIZED_AT, "summarized_at", SQLDataType.TIMESTAMPWITHTIMEZONE)

        assertField(Tables.ChatKnowledgeSummaries.PEER_ID, "peer_id", SQLDataType.BIGINT)
        assertField(Tables.ChatKnowledgeSummaries.SUMMARY_TEXT, "summary_text", SQLDataType.OTHER)
        assertField(Tables.ChatKnowledgeSummaries.MESSAGES_COVERED, "messages_covered", SQLDataType.INTEGER)
        assertField(
            Tables.ChatKnowledgeSummaries.FIRST_CONVERSATION_MESSAGE_ID,
            "first_conversation_message_id",
            SQLDataType.BIGINT,
        )
        assertField(
            Tables.ChatKnowledgeSummaries.LAST_CONVERSATION_MESSAGE_ID,
            "last_conversation_message_id",
            SQLDataType.BIGINT,
        )
        assertField(Tables.ChatKnowledgeSummaries.SUMMARIZED_AT, "summarized_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
    }

    private fun assertCursorFields() {
        assertField(Tables.KnowledgeExtractionCursors.PEER_ID, "peer_id", SQLDataType.BIGINT)
        assertField(
            Tables.KnowledgeExtractionCursors.LAST_CONVERSATION_MESSAGE_ID,
            "last_conversation_message_id",
            SQLDataType.BIGINT,
        )
        assertField(
            Tables.KnowledgeExtractionCursors.LAST_COMPLETED_AT,
            "last_completed_at",
            SQLDataType.TIMESTAMPWITHTIMEZONE,
        )
        assertField(
            Tables.KnowledgeExtractionCursors.UPDATED_AT,
            "updated_at",
            SQLDataType.TIMESTAMPWITHTIMEZONE,
        )
    }

    private fun assertOnlyExpectedConstants() {
        assertConstants(
            Tables.UserFacts,
            "ID", "USER_ID", "PEER_ID", "CATEGORY", "FACT_KEY", "VALUE", "VALUE_HASH", "VALUE_PAYLOAD",
            "EVIDENCE_COUNT", "MAX_EVIDENCE_CONFIDENCE", "CONFIDENCE", "STATUS", "FIRST_OBSERVED_AT",
            "LAST_CONFIRMED_AT", "SUPERSEDED_AT", "CONFLICT", "CREATED_AT", "UPDATED_AT",
        )
        assertConstants(
            Tables.UserFactEvidence,
            "ID", "FACT_ID", "MESSAGE_ID", "CONVERSATION_MESSAGE_ID", "PEER_ID", "SAID_BY_ID", "SOURCE_KIND",
            "CONFIDENCE", "OBSERVED_AT",
        )
        assertConstants(
            Tables.UserKnowledgeSummaries,
            "USER_ID",
            "SUMMARY_TEXT",
            "FACTS_COVERED",
            "LAST_FACT_AT",
            "SUMMARIZED_AT",
        )
        assertConstants(
            Tables.ChatKnowledgeSummaries,
            "PEER_ID",
            "SUMMARY_TEXT",
            "MESSAGES_COVERED",
            "FIRST_CONVERSATION_MESSAGE_ID",
            "LAST_CONVERSATION_MESSAGE_ID",
            "SUMMARIZED_AT",
        )
        assertConstants(
            Tables.KnowledgeExtractionCursors,
            "PEER_ID",
            "LAST_CONVERSATION_MESSAGE_ID",
            "LAST_COMPLETED_AT",
            "UPDATED_AT",
        )
    }

    private fun assertField(field: Field<*>, name: String, dataType: DataType<*>) {
        assertEquals(name, field.name)
        assertEquals(dataType, field.dataType)
    }

    private fun assertConstants(holder: Any, vararg expected: String) {
        val actual = holder::class.memberProperties.map { it.name }.filter { it != "TABLE" }
        assertEquals(expected.sorted(), actual.sorted())
    }
}

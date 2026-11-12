package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType

object Tables {

    object Messages {
        val TABLE = DSL.table("messages")

        val ID = DSL.field("id")
        val DATE = DSL.field("date")
        val FROM_ID = DSL.field("from_id")
        val GROUP_ID = DSL.field("group_id")
        val PEER_ID = DSL.field("peer_id")
        val CONVERSATION_MESSAGE_ID = DSL.field("conversation_message_id")
        val MESSAGE_TEXT = DSL.field("message_text")
        val FORWARDED_MESSAGES = DSL.field("forwarded_messages", SQLDataType.JSONB)
    }

    object VkUserProfiles {
        val TABLE = DSL.table("vk_user_profiles")

        val ID = DSL.field("id")
        val FIRST_NAME = DSL.field("first_name")
        val LAST_NAME = DSL.field("last_name")
        val SCREEN_NAME = DSL.field("screen_name")
        val BIRTH_DATE = DSL.field("birth_date")
        val CITY = DSL.field("city")
        val PROFILE_DETAILS = DSL.field("profile_details", SQLDataType.JSONB)
        val LAST_UPDATED = DSL.field("last_updated")
        val CREATED_AT = DSL.field("created_at")
    }

    object VkUserProfileHistory {
        val TABLE = DSL.table("vk_user_profile_history")

        val ID = DSL.field("id")
        val PROFILE_ID = DSL.field("profile_id")
        val CHANGES = DSL.field("changes", SQLDataType.JSONB)
        val CHANGED_AT = DSL.field("changed_at")
    }

    object VkGroupProfiles {
        val TABLE = DSL.table("vk_group_profiles")

        val ID = DSL.field("id")
        val NAME = DSL.field("name")
        val SCREEN_NAME = DSL.field("screen_name")
        val LAST_UPDATED = DSL.field("last_updated")
        val CREATED_AT = DSL.field("created_at")
    }

    object Summaries {
        val TABLE = DSL.table("summaries")

        val ID = DSL.field("id")
        val PEER_ID = DSL.field("peer_id")
        val FIRST_MESSAGE_ID = DSL.field("first_message_id")
        val LAST_MESSAGE_ID = DSL.field("last_message_id")
        val FULL_SUMMARY = DSL.field("full_summary")
        val SHORT_SUMMARY = DSL.field("short_summary")
        val STATUS = DSL.field("status")
        val CREATED_AT = DSL.field("created_at")
        val UPDATED_AT = DSL.field("updated_at")
    }

    object LongPollPosition {
        val TABLE = DSL.table("long_poll_position")

        val ID = DSL.field("id")
        val TS = DSL.field("ts")
    }

    object UserStatisticsTable {
        val TABLE = DSL.table("user_statistics")

        val USER_ID = DSL.field("user_id", SQLDataType.BIGINT)
        val MESSAGE_COUNT = DSL.field("message_count", SQLDataType.BIGINT)
        val TOTAL_LENGTH = DSL.field("total_length", SQLDataType.BIGINT)
        val FIRST_MESSAGE_AT = DSL.field("first_message_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
        val LAST_MESSAGE_AT = DSL.field("last_message_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
        val UPDATED_AT = DSL.field("updated_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
    }

    object UserChatStatisticsTable {
        val TABLE = DSL.table("user_chat_statistics")

        val USER_ID = DSL.field("user_id", SQLDataType.BIGINT)
        val PEER_ID = DSL.field("peer_id", SQLDataType.BIGINT)
        val MESSAGE_COUNT = DSL.field("message_count", SQLDataType.BIGINT)
        val TOTAL_LENGTH = DSL.field("total_length", SQLDataType.BIGINT)
        val LAST_MESSAGE_AT = DSL.field("last_message_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
    }

    object UserHourStatisticsTable {
        val TABLE = DSL.table("user_hour_statistics")

        val USER_ID = DSL.field("user_id", SQLDataType.BIGINT)
        val UTC_DATE = DSL.field("utc_date", SQLDataType.LOCALDATE)
        val UTC_HOUR = DSL.field("utc_hour", SQLDataType.SMALLINT)
        val MESSAGE_COUNT = DSL.field("message_count", SQLDataType.BIGINT)
    }

    object GreetingLog {
        val TABLE = DSL.table("greeting_log")

        val GREETING_DATE = DSL.field("greeting_date", SQLDataType.DATE)
        val PEER_ID = DSL.field("peer_id", SQLDataType.BIGINT)
        val CREATED_AT = DSL.field("created_at")
    }

    object UserFacts {
        val TABLE = DSL.table("user_facts")

        val ID = DSL.field("id", SQLDataType.UUID)
        val USER_ID = DSL.field("user_id", SQLDataType.BIGINT)
        val PEER_ID = DSL.field("peer_id", SQLDataType.BIGINT)
        val CATEGORY = DSL.field("category")
        val FACT_KEY = DSL.field("fact_key")
        val VALUE = DSL.field("value")
        val VALUE_HASH = DSL.field("value_hash")
        val VALUE_PAYLOAD = DSL.field("value_payload", SQLDataType.JSONB)
        val EVIDENCE_COUNT = DSL.field("evidence_count", SQLDataType.INTEGER)
        val MAX_EVIDENCE_CONFIDENCE = DSL.field("max_evidence_confidence", SQLDataType.NUMERIC)
        val CONFIDENCE = DSL.field("confidence", SQLDataType.NUMERIC)
        val STATUS = DSL.field("status")
        val FIRST_OBSERVED_AT = DSL.field("first_observed_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
        val LAST_CONFIRMED_AT = DSL.field("last_confirmed_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
        val SUPERSEDED_AT = DSL.field("superseded_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
        val CONFLICT = DSL.field("conflict", SQLDataType.BOOLEAN)
        val CREATED_AT = DSL.field("created_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
        val UPDATED_AT = DSL.field("updated_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
    }

    object UserFactEvidence {
        val TABLE = DSL.table("user_fact_evidence")

        val ID = DSL.field("id", SQLDataType.UUID)
        val FACT_ID = DSL.field("fact_id", SQLDataType.UUID)
        val MESSAGE_ID = DSL.field("message_id", SQLDataType.UUID)
        val CONVERSATION_MESSAGE_ID = DSL.field("conversation_message_id", SQLDataType.BIGINT)
        val PEER_ID = DSL.field("peer_id", SQLDataType.BIGINT)
        val SAID_BY_ID = DSL.field("said_by_id", SQLDataType.BIGINT)
        val SOURCE_KIND = DSL.field("source_kind")
        val CONFIDENCE = DSL.field("confidence", SQLDataType.NUMERIC)
        val OBSERVED_AT = DSL.field("observed_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
    }

    object UserKnowledgeSummaries {
        val TABLE = DSL.table("user_knowledge_summary")

        val USER_ID = DSL.field("user_id", SQLDataType.BIGINT)
        val SUMMARY_TEXT = DSL.field("summary_text")
        val FACTS_COVERED = DSL.field("facts_covered", SQLDataType.INTEGER)
        val LAST_FACT_AT = DSL.field("last_fact_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
        val SUMMARIZED_AT = DSL.field("summarized_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
    }

    object ChatKnowledgeSummaries {
        val TABLE = DSL.table("chat_knowledge_summary")

        val PEER_ID = DSL.field("peer_id", SQLDataType.BIGINT)
        val SUMMARY_TEXT = DSL.field("summary_text")
        val MESSAGES_COVERED = DSL.field("messages_covered", SQLDataType.INTEGER)
        val FIRST_CONVERSATION_MESSAGE_ID = DSL.field("first_conversation_message_id", SQLDataType.BIGINT)
        val LAST_CONVERSATION_MESSAGE_ID = DSL.field("last_conversation_message_id", SQLDataType.BIGINT)
        val SUMMARIZED_AT = DSL.field("summarized_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
    }

    object KnowledgeExtractionCursors {
        val TABLE = DSL.table("knowledge_extraction_cursors")

        val PEER_ID = DSL.field("peer_id", SQLDataType.BIGINT)
        val LAST_CONVERSATION_MESSAGE_ID = DSL.field("last_conversation_message_id", SQLDataType.BIGINT)
        val LAST_COMPLETED_AT = DSL.field("last_completed_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
        val UPDATED_AT = DSL.field("updated_at", SQLDataType.TIMESTAMPWITHTIMEZONE)
    }
}

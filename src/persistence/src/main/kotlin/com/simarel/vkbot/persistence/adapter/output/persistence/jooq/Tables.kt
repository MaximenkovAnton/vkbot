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
}

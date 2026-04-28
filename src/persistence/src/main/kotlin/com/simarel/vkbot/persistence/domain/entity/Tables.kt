package com.simarel.vkbot.persistence.domain.entity

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
        val LAST_UPDATED = DSL.field("last_updated")
        val CREATED_AT = DSL.field("created_at")
    }

    object VkGroupProfiles {
        val TABLE = DSL.table("vk_group_profiles")

        val ID = DSL.field("id")
        val NAME = DSL.field("name")
        val SCREEN_NAME = DSL.field("screen_name")
        val LAST_UPDATED = DSL.field("last_updated")
        val CREATED_AT = DSL.field("created_at")
    }
}

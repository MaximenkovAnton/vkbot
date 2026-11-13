package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.UserChatStatisticsTable
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.UserHourStatisticsTable
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.UserStatisticsTable
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import java.time.OffsetDateTime
import java.time.ZoneOffset

@ApplicationScoped
open class UserStatisticsWriter {

    @Inject
    lateinit var dsl: DSLContext

    open fun upsertTotals(userId: Long, date: OffsetDateTime, messageTextLength: Int) {
        dsl.insertInto(UserStatisticsTable.TABLE)
            .set(UserStatisticsTable.USER_ID, userId)
            .set(UserStatisticsTable.MESSAGE_COUNT, 1L)
            .set(UserStatisticsTable.TOTAL_LENGTH, messageTextLength.toLong())
            .set(UserStatisticsTable.FIRST_MESSAGE_AT, date)
            .set(UserStatisticsTable.LAST_MESSAGE_AT, date)
            .set(UserStatisticsTable.UPDATED_AT, DSL.currentOffsetDateTime())
            .onConflict(UserStatisticsTable.USER_ID)
            .doUpdate()
            .set(UserStatisticsTable.MESSAGE_COUNT, TOTAL_MESSAGE_COUNT)
            .set(UserStatisticsTable.TOTAL_LENGTH, TOTAL_LENGTH)
            .set(UserStatisticsTable.FIRST_MESSAGE_AT, EARLIEST_MESSAGE_AT)
            .set(UserStatisticsTable.LAST_MESSAGE_AT, LATEST_MESSAGE_AT)
            .set(UserStatisticsTable.UPDATED_AT, DSL.currentOffsetDateTime())
            .execute()
    }

    open fun upsertChat(userId: Long, peerId: Long, date: OffsetDateTime, messageTextLength: Int) {
        dsl.insertInto(UserChatStatisticsTable.TABLE)
            .set(UserChatStatisticsTable.USER_ID, userId)
            .set(UserChatStatisticsTable.PEER_ID, peerId)
            .set(UserChatStatisticsTable.MESSAGE_COUNT, 1L)
            .set(UserChatStatisticsTable.TOTAL_LENGTH, messageTextLength.toLong())
            .set(UserChatStatisticsTable.LAST_MESSAGE_AT, date)
            .onConflict(UserChatStatisticsTable.USER_ID, UserChatStatisticsTable.PEER_ID)
            .doUpdate()
            .set(UserChatStatisticsTable.MESSAGE_COUNT, CHAT_MESSAGE_COUNT)
            .set(UserChatStatisticsTable.TOTAL_LENGTH, CHAT_TOTAL_LENGTH)
            .set(UserChatStatisticsTable.LAST_MESSAGE_AT, LATEST_CHAT_MESSAGE_AT)
            .execute()
    }

    open fun upsertHour(userId: Long, date: OffsetDateTime) {
        val utc = date.withOffsetSameInstant(ZoneOffset.UTC)
        dsl.insertInto(UserHourStatisticsTable.TABLE)
            .set(UserHourStatisticsTable.USER_ID, userId)
            .set(UserHourStatisticsTable.UTC_DATE, utc.toLocalDate())
            .set(UserHourStatisticsTable.UTC_HOUR, utc.hour.toShort())
            .set(UserHourStatisticsTable.MESSAGE_COUNT, 1L)
            .onConflict(
                UserHourStatisticsTable.USER_ID,
                UserHourStatisticsTable.UTC_DATE,
                UserHourStatisticsTable.UTC_HOUR,
            )
            .doUpdate()
            .set(UserHourStatisticsTable.MESSAGE_COUNT, HOUR_MESSAGE_COUNT)
            .execute()
    }

    private companion object {
        val TOTAL_MESSAGE_COUNT: Field<Long> =
            DSL.field("user_statistics.message_count + 1", Long::class.java)
        val TOTAL_LENGTH: Field<Long> =
            DSL.field("user_statistics.total_length + excluded.total_length", Long::class.java)
        val EARLIEST_MESSAGE_AT: Field<OffsetDateTime> = DSL.field(
            "least(user_statistics.first_message_at, excluded.first_message_at)",
            OffsetDateTime::class.java,
        )
        val LATEST_MESSAGE_AT: Field<OffsetDateTime> = DSL.field(
            "greatest(user_statistics.last_message_at, excluded.last_message_at)",
            OffsetDateTime::class.java,
        )
        val CHAT_MESSAGE_COUNT: Field<Long> =
            DSL.field("user_chat_statistics.message_count + 1", Long::class.java)
        val CHAT_TOTAL_LENGTH: Field<Long> =
            DSL.field("user_chat_statistics.total_length + excluded.total_length", Long::class.java)
        val LATEST_CHAT_MESSAGE_AT: Field<OffsetDateTime> = DSL.field(
            "greatest(user_chat_statistics.last_message_at, excluded.last_message_at)",
            OffsetDateTime::class.java,
        )
        val HOUR_MESSAGE_COUNT: Field<Long> =
            DSL.field("user_hour_statistics.message_count + 1", Long::class.java)
    }
}

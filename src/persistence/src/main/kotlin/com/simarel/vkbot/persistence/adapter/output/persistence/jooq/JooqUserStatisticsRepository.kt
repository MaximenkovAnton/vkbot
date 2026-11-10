package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.UserChatStatisticsTable
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.UserHourStatisticsTable
import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.UserStatisticsTable
import com.simarel.vkbot.share.domain.model.ChatStatistics
import com.simarel.vkbot.share.domain.model.DayOfWeekActivity
import com.simarel.vkbot.share.domain.model.HourActivity
import com.simarel.vkbot.share.domain.model.UserStatistics
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import java.time.OffsetDateTime
import java.time.ZoneId

@ApplicationScoped
open class JooqUserStatisticsRepository {

    @Inject
    lateinit var dsl: DSLContext

    open fun findByUser(userId: Long, zone: ZoneId): UserStatistics {
        val totals = dsl.select(MESSAGE_COUNT, TOTAL_LENGTH, FIRST_DATE, LAST_DATE)
            .from(UserStatisticsTable.TABLE)
            .where(UserStatisticsTable.USER_ID.eq(userId))
            .fetchOne()

        val messageCount = totals?.get(MESSAGE_COUNT, Long::class.java) ?: 0L
        val totalLength = totals?.get(TOTAL_LENGTH, Long::class.java) ?: 0L

        return UserStatistics(
            userId = userId,
            messageCount = messageCount,
            totalLength = totalLength,
            averageLength = averageLength(totalLength, messageCount),
            firstMessageAt = totals?.get(FIRST_DATE, OffsetDateTime::class.java),
            lastMessageAt = totals?.get(LAST_DATE, OffsetDateTime::class.java),
            chats = findChats(userId),
            activityByHour = findActivityByHour(userId, zone),
            activityByDayOfWeek = findActivityByDayOfWeek(userId, zone),
        )
    }

    private fun findChats(userId: Long): List<ChatStatistics> = dsl.select(
        UserChatStatisticsTable.PEER_ID,
        UserChatStatisticsTable.MESSAGE_COUNT,
        UserChatStatisticsTable.TOTAL_LENGTH,
        UserChatStatisticsTable.LAST_MESSAGE_AT,
    )
        .from(UserChatStatisticsTable.TABLE)
        .where(UserChatStatisticsTable.USER_ID.eq(userId))
        .orderBy(UserChatStatisticsTable.MESSAGE_COUNT.desc(), UserChatStatisticsTable.PEER_ID.asc())
        .fetch()
        .map { record ->
            val messageCount = record.get(UserChatStatisticsTable.MESSAGE_COUNT, Long::class.java)
            val totalLength = record.get(UserChatStatisticsTable.TOTAL_LENGTH, Long::class.java)
            ChatStatistics(
                peerId = record.get(UserChatStatisticsTable.PEER_ID, Long::class.java),
                messageCount = messageCount,
                totalLength = totalLength,
                averageLength = averageLength(totalLength, messageCount),
                lastMessageAt = record.get(UserChatStatisticsTable.LAST_MESSAGE_AT, OffsetDateTime::class.java),
            )
        }

    private fun findActivityByHour(userId: Long, zone: ZoneId): List<HourActivity> {
        val hour = hourOfDay(zone)
        return dsl.select(hour, BUCKET_MESSAGE_COUNT)
            .from(UserHourStatisticsTable.TABLE)
            .where(UserHourStatisticsTable.USER_ID.eq(userId))
            .groupBy(hour)
            .orderBy(hour.asc())
            .fetch()
            .map { record ->
                HourActivity(
                    hour = record.get(hour, Int::class.javaObjectType),
                    messageCount = record.get(BUCKET_MESSAGE_COUNT, Long::class.java),
                )
            }
    }

    private fun findActivityByDayOfWeek(userId: Long, zone: ZoneId): List<DayOfWeekActivity> {
        val dayOfWeek = dayOfWeek(zone)
        return dsl.select(dayOfWeek, BUCKET_MESSAGE_COUNT)
            .from(UserHourStatisticsTable.TABLE)
            .where(UserHourStatisticsTable.USER_ID.eq(userId))
            .groupBy(dayOfWeek)
            .orderBy(dayOfWeek.asc())
            .fetch()
            .map { record ->
                DayOfWeekActivity(
                    dayOfWeek = record.get(dayOfWeek, Int::class.javaObjectType),
                    messageCount = record.get(BUCKET_MESSAGE_COUNT, Long::class.java),
                )
            }
    }

    private fun hourOfDay(zone: ZoneId): Field<Int> = DSL.field(
        "extract(hour from (utc_date + utc_hour * interval '1 hour') at time zone 'UTC' at time zone {0})::int",
        Int::class.javaObjectType,
        DSL.inline(zone.id),
    )

    private fun dayOfWeek(zone: ZoneId): Field<Int> = DSL.field(
        "extract(isodow from (utc_date + utc_hour * interval '1 hour') at time zone 'UTC' at time zone {0})::int",
        Int::class.javaObjectType,
        DSL.inline(zone.id),
    )

    private fun averageLength(totalLength: Long, messageCount: Long): Double {
        if (messageCount == 0L) {
            return 0.0
        }
        return totalLength.toDouble() / messageCount
    }

    private companion object {
        val MESSAGE_COUNT: Field<Long> = DSL.field("message_count", Long::class.java)
        val TOTAL_LENGTH: Field<Long> = DSL.field("total_length", Long::class.java)
        val FIRST_DATE: Field<OffsetDateTime> = DSL.field("first_message_at", OffsetDateTime::class.java)
        val LAST_DATE: Field<OffsetDateTime> = DSL.field("last_message_at", OffsetDateTime::class.java)
        val BUCKET_MESSAGE_COUNT: Field<Long> = DSL.field("sum(message_count)::bigint", Long::class.java)
    }
}

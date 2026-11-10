package com.simarel.vkbot.persistence.adapter.output.persistence.jooq

import com.simarel.vkbot.persistence.adapter.output.persistence.jooq.Tables.Messages
import com.simarel.vkbot.persistence.domain.statistics.ChatStatistics
import com.simarel.vkbot.persistence.domain.statistics.DayOfWeekActivity
import com.simarel.vkbot.persistence.domain.statistics.HourActivity
import com.simarel.vkbot.persistence.domain.statistics.UserStatistics
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
        val totals = dsl.select(COUNT, TOTAL_LENGTH, FIRST_DATE, LAST_DATE)
            .from(Messages.TABLE)
            .where(Messages.FROM_ID.eq(userId))
            .fetchOne()

        val messageCount = totals?.get(COUNT, Long::class.java) ?: 0L
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
        Messages.PEER_ID,
        COUNT,
        TOTAL_LENGTH,
        LAST_DATE,
    )
        .from(Messages.TABLE)
        .where(Messages.FROM_ID.eq(userId))
        .groupBy(Messages.PEER_ID)
        .orderBy(COUNT.desc())
        .fetch()
        .map { record ->
            val messageCount = record.get(COUNT, Long::class.java)
            val totalLength = record.get(TOTAL_LENGTH, Long::class.java)
            ChatStatistics(
                peerId = record.get(Messages.PEER_ID, Long::class.java),
                messageCount = messageCount,
                totalLength = totalLength,
                averageLength = averageLength(totalLength, messageCount),
                lastMessageAt = record.get(LAST_DATE, OffsetDateTime::class.java),
            )
        }

    private fun findActivityByHour(userId: Long, zone: ZoneId): List<HourActivity> {
        val hour = hourOfDay(zone)
        return dsl.select(hour, COUNT)
            .from(Messages.TABLE)
            .where(Messages.FROM_ID.eq(userId))
            .groupBy(hour)
            .orderBy(hour.asc())
            .fetch()
            .map { record ->
                HourActivity(
                    hour = record.get(hour, Int::class.javaObjectType),
                    messageCount = record.get(COUNT, Long::class.java),
                )
            }
    }

    private fun findActivityByDayOfWeek(userId: Long, zone: ZoneId): List<DayOfWeekActivity> {
        val dayOfWeek = dayOfWeek(zone)
        return dsl.select(dayOfWeek, COUNT)
            .from(Messages.TABLE)
            .where(Messages.FROM_ID.eq(userId))
            .groupBy(dayOfWeek)
            .orderBy(dayOfWeek.asc())
            .fetch()
            .map { record ->
                DayOfWeekActivity(
                    dayOfWeek = record.get(dayOfWeek, Int::class.javaObjectType),
                    messageCount = record.get(COUNT, Long::class.java),
                )
            }
    }

    private fun hourOfDay(zone: ZoneId): Field<Int> = DSL.field(
        "extract(hour from (date at time zone {0}))::int",
        Int::class.javaObjectType,
        DSL.inline(zone.id),
    )

    private fun dayOfWeek(zone: ZoneId): Field<Int> = DSL.field(
        "extract(isodow from (date at time zone {0}))::int",
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
        val COUNT: Field<Long> = DSL.field("count(*)", Long::class.java)
        val TOTAL_LENGTH: Field<Long> = DSL.field("coalesce(sum(length(message_text)), 0)", Long::class.java)
        val FIRST_DATE: Field<OffsetDateTime> = DSL.field("min(date)", OffsetDateTime::class.java)
        val LAST_DATE: Field<OffsetDateTime> = DSL.field("max(date)", OffsetDateTime::class.java)
    }
}

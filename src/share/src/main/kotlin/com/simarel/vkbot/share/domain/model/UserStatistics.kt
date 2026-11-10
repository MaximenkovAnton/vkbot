package com.simarel.vkbot.share.domain.model

import java.time.OffsetDateTime

data class UserStatistics(
    val userId: Long,
    val messageCount: Long,
    val totalLength: Long,
    val averageLength: Double,
    val firstMessageAt: OffsetDateTime?,
    val lastMessageAt: OffsetDateTime?,
    val chats: List<ChatStatistics>,
    val activityByHour: List<HourActivity>,
    val activityByDayOfWeek: List<DayOfWeekActivity>,
)

data class ChatStatistics(
    val peerId: Long,
    val messageCount: Long,
    val totalLength: Long,
    val averageLength: Double,
    val lastMessageAt: OffsetDateTime?,
)

data class HourActivity(
    val hour: Int,
    val messageCount: Long,
)

data class DayOfWeekActivity(
    val dayOfWeek: Int,
    val messageCount: Long,
)

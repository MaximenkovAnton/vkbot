package com.simarel.vkbot.ai.domain.news

import java.time.OffsetDateTime

data class NewsPeriod(
    val from: OffsetDateTime,
    val to: OffsetDateTime,
)

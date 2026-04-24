package com.simarel.vkbot.infrastructure.config

import io.smallrye.config.ConfigMapping
import io.smallrye.config.WithName

@ConfigMapping(prefix = "dlq")
interface DlqConfig {
    @WithName("levels")
    fun levels(): List<DlqLevel>

    interface DlqLevel {
        fun retryCount(): Int
        fun queueName(): String
        fun ttlMs(): Long
        fun exchangeName(): String
    }
}

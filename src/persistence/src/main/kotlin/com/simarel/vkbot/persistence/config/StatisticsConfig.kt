package com.simarel.vkbot.persistence.config

import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.ZoneId

@ApplicationScoped
class StatisticsConfig(
    @ConfigProperty(name = "statistics.timezone", defaultValue = "Europe/Minsk")
    timezone: String,
) {
    val zone: ZoneId = ZoneId.of(timezone)
}

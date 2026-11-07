package com.simarel.vkbot.greetings.adapter.input.schedule

import com.simarel.vkbot.greetings.port.input.RunDailyGreetingsInputPort
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@ApplicationScoped
open class DailyGreetingRunner(
    private val runDailyGreetings: RunDailyGreetingsInputPort,
    private val tracer: Tracer,
    @param:ConfigProperty(name = "greetings.timezone", defaultValue = "Europe/Minsk") timezone: String,
) {
    private val zone = ZoneId.of(timezone)

    @Scheduled(identity = "daily-greetings", cron = "{greetings.cron}", timeZone = "{greetings.timezone}")
    open fun runScheduled() {
        runOnce(LocalDate.now(zone))
    }

    @Suppress("TooGenericExceptionCaught")
    open fun runOnce(date: LocalDate) {
        val span = tracer.spanBuilder(SPAN_NAME).startSpan()
        span.setAttribute(DATE_ATTRIBUTE, date.format(DateTimeFormatter.ISO_LOCAL_DATE))
        try {
            span.makeCurrent().use {
                val response = runDailyGreetings.execute(RunDailyGreetingsInputPort.Request(date))
                span.setAttribute(PLANNED_ATTRIBUTE, response.planned.toLong())
                span.setAttribute(SENT_ATTRIBUTE, response.sent.toLong())
                span.setAttribute(FAILED_ATTRIBUTE, response.failed.toLong())
                span.setStatus(StatusCode.OK)
            }
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            span.setStatus(StatusCode.ERROR, e.message ?: e.javaClass.name)
            throw e
        } finally {
            span.end()
        }
    }

    private companion object {
        const val SPAN_NAME = "greetings.daily-run"
        const val DATE_ATTRIBUTE = "date"
        const val PLANNED_ATTRIBUTE = "planned"
        const val SENT_ATTRIBUTE = "sent"
        const val FAILED_ATTRIBUTE = "failed"
    }
}

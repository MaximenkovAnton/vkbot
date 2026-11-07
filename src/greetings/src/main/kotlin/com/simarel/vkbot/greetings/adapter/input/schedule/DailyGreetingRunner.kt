package com.simarel.vkbot.greetings.adapter.input.schedule

import com.simarel.vkbot.greetings.domain.NextRun
import com.simarel.vkbot.greetings.port.input.RunDailyGreetingsInputPort
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.lang.System.Logger.Level
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@ApplicationScoped
open class DailyGreetingRunner(
    private val runDailyGreetings: RunDailyGreetingsInputPort,
    private val tracer: Tracer,
    @param:ConfigProperty(name = "greetings.enabled", defaultValue = "true") private val enabled: Boolean,
    @param:ConfigProperty(name = "greetings.timezone", defaultValue = "Europe/Minsk") timezone: String,
) {
    private val zone = ZoneId.of(timezone)
    private val logger = System.getLogger(DailyGreetingRunner::class.java.name)

    @Volatile
    var runThread: Thread? = null
        private set

    @Volatile
    private var running = false

    @Suppress("UnusedParameter")
    fun start(@Observes event: StartupEvent) {
        if (!enabled) {
            logger.log(Level.INFO, "Daily greetings runner is disabled")
            return
        }
        if (running) {
            return
        }
        running = true
        runThread = Thread.ofVirtual().name(RUN_THREAD_NAME).start(::loop)
    }

    @Suppress("UnusedParameter")
    fun stop(@Observes event: ShutdownEvent) {
        running = false
        runThread?.interrupt()
    }

    internal open fun now(): ZonedDateTime = ZonedDateTime.now(zone)

    @Suppress("TooGenericExceptionCaught")
    internal open fun loop() {
        while (running) {
            try {
                runScheduled(now())
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            } catch (e: Exception) {
                logger.log(Level.ERROR, "Daily greetings run failed, waiting for the next 00:00", e)
            }
        }
    }

    internal open fun runScheduled(from: ZonedDateTime) {
        val next = NextRun.after(from, zone)
        await(next)
        runOnce(next.target.toLocalDate())
    }

    internal open fun await(next: NextRun) {
        var delay = next.delay(now())
        while (delay.isPositive) {
            pauseNanos(delay.toNanos())
            delay = next.delay(now())
        }
    }

    internal open fun pauseNanos(nanos: Long) {
        Thread.sleep(nanos / NANOS_PER_MILLI, (nanos % NANOS_PER_MILLI).toInt())
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
        const val RUN_THREAD_NAME = "daily-greeting-runner"
        const val SPAN_NAME = "greetings.daily-run"
        const val DATE_ATTRIBUTE = "date"
        const val PLANNED_ATTRIBUTE = "planned"
        const val SENT_ATTRIBUTE = "sent"
        const val FAILED_ATTRIBUTE = "failed"
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

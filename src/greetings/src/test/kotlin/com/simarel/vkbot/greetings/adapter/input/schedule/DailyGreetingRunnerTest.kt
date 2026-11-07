package com.simarel.vkbot.greetings.adapter.input.schedule

import com.simarel.vkbot.greetings.test.FakeClockRunner
import com.simarel.vkbot.greetings.test.FakeRunDailyGreetingsInputPort
import com.simarel.vkbot.greetings.test.SystemLoggerCapture
import com.simarel.vkbot.greetings.testfixtures.FakeGreetingProvider
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class DailyGreetingRunnerTest {

    @Test
    fun `runScheduled calls the port once with the date of the target midnight`() {
        val input = FakeRunDailyGreetingsInputPort()
        val runner = runner(clock = LATE_EVENING, input = input)

        runner.runScheduled(runner.clock)

        assertEquals(listOf(LocalDate.parse("2027-01-01")), input.dates)
    }

    @Test
    fun `an early wake does not lose or duplicate the target date`() {
        val input = FakeRunDailyGreetingsInputPort()
        val runner = runner(
            clock = NOON,
            input = input,
            pauseDurations = listOf(Duration.ofHours(12).toNanos() - HALF_MILLIS_NANOS),
        )

        runner.runScheduled(runner.clock)

        assertEquals(listOf(LocalDate.parse("2027-01-01")), input.dates)
        assertEquals(2, runner.pauses.size)
    }

    @Test
    fun `a disabled runner does not start`() {
        val input = FakeRunDailyGreetingsInputPort()
        val runner = runner(clock = NOON, input = input, enabled = false)

        runner.start(StartupEvent())

        assertNull(runner.runThread)
        assertTrue(input.executeCalls.isEmpty())
    }

    @Test
    fun `a second start does not create a second thread`() {
        val runner = runner(clock = NOON, autoAdvance = false)

        runner.start(StartupEvent())
        val thread = requireNotNull(runner.runThread)
        runner.start(StartupEvent())

        assertEquals(thread, runner.runThread)
        runner.stop(ShutdownEvent())
        thread.join(THREAD_TIMEOUT_MILLIS)

        assertFalse(thread.isAlive)
    }

    @Test
    fun `a stop during the wait interrupts it without calling the port or closing a span`() {
        val exporter = InMemorySpanExporter.create()
        val input = FakeRunDailyGreetingsInputPort()
        val runner = runner(clock = NOON, input = input, exporter = exporter, autoAdvance = false)

        runner.start(StartupEvent())
        assertTrue(runner.enteredWait.await(THREAD_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS))
        runner.stop(ShutdownEvent())
        val thread = requireNotNull(runner.runThread)
        thread.join(THREAD_TIMEOUT_MILLIS)

        assertFalse(thread.isAlive)
        assertTrue(input.executeCalls.isEmpty())
        assertTrue(exporter.finishedSpanItems.isEmpty())
    }

    @Test
    fun `a successful run closes the span with OK and the counters`() {
        val exporter = InMemorySpanExporter.create()
        val input = FakeRunDailyGreetingsInputPort {
            FakeGreetingProvider.createRunDailyGreetingsResponse(planned = 2, sent = 1, failed = 1)
        }
        val runner = runner(clock = NOON, input = input, exporter = exporter)

        runner.runOnce(RUN_DATE)

        val span = exporter.finishedSpanItems.single()
        assertEquals(SPAN_NAME, span.name)
        assertEquals(RUN_DATE.toString(), span.attributes.get(AttributeKey.stringKey("date")))
        assertEquals(2L, span.attributes.get(AttributeKey.longKey("planned")))
        assertEquals(1L, span.attributes.get(AttributeKey.longKey("sent")))
        assertEquals(1L, span.attributes.get(AttributeKey.longKey("failed")))
        assertEquals(StatusCode.OK, span.status.statusCode)
    }

    @Test
    fun `a failed run closes the span with ERROR and only the date`() {
        val exporter = InMemorySpanExporter.create()
        val input = FakeRunDailyGreetingsInputPort { throw IllegalStateException("collection failed") }
        val runner = runner(clock = NOON, input = input, exporter = exporter)

        val thrown = assertThrows<IllegalStateException> { runner.runOnce(RUN_DATE) }

        assertEquals("collection failed", thrown.message)
        val span = exporter.finishedSpanItems.single()
        assertEquals(setOf("date"), span.attributes.asMap().keys.map { it.key }.toSet())
        assertEquals(StatusCode.ERROR, span.status.statusCode)
    }

    @Test
    fun `the loop logs the failure and waits for the next midnight`() {
        var calls = 0
        val input = FakeRunDailyGreetingsInputPort {
            calls++
            when (calls) {
                FIRST_RUN -> error("first run failed")
                INTERRUPTING_RUN -> throw InterruptedException()
                else -> FakeGreetingProvider.createRunDailyGreetingsResponse()
            }
        }
        val exporter = InMemorySpanExporter.create()

        SystemLoggerCapture(DailyGreetingRunner::class.java.name).use { capture ->
            val runner = runner(
                clock = ZonedDateTime.of(2027, 12, 30, 12, 0, 0, 0, ZONE),
                input = input,
                exporter = exporter,
            )
            runner.start(StartupEvent())
            val thread = requireNotNull(runner.runThread)
            thread.join(THREAD_TIMEOUT_MILLIS)

            assertFalse(thread.isAlive)
            assertEquals(LocalDate.parse("2027-12-31"), input.dates.first())
            assertEquals(LocalDate.parse("2028-01-02"), input.dates.last())
            assertEquals(
                listOf(StatusCode.ERROR, StatusCode.OK, StatusCode.UNSET),
                exporter.finishedSpanItems.map { it.status.statusCode },
            )
            assertTrue(capture.messages(Level.SEVERE).any { it.contains("Daily greetings run failed") })
        }
    }

    @Test
    fun `an interruption inside the run is not reported as ERROR`() {
        val exporter = InMemorySpanExporter.create()
        val input = FakeRunDailyGreetingsInputPort { throw InterruptedException() }
        val runner = runner(clock = NOON, input = input, exporter = exporter)

        assertThrows<InterruptedException> { runner.runOnce(RUN_DATE) }

        val span = exporter.finishedSpanItems.single()
        assertNotEquals(StatusCode.ERROR, span.status.statusCode)
    }

    private fun runner(
        clock: ZonedDateTime,
        input: FakeRunDailyGreetingsInputPort = FakeRunDailyGreetingsInputPort(),
        exporter: InMemorySpanExporter = InMemorySpanExporter.create(),
        pauseDurations: List<Long> = emptyList(),
        autoAdvance: Boolean = true,
        enabled: Boolean = true,
    ): FakeClockRunner = FakeClockRunner(
        clock = clock,
        input = input,
        tracer = tracer(exporter),
        pauseDurations = pauseDurations,
        autoAdvance = autoAdvance,
        enabled = enabled,
    )

    private fun tracer(exporter: InMemorySpanExporter): Tracer = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
        .build()
        .get("daily-greetings-test")

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Minsk")
        val NOON: ZonedDateTime = ZonedDateTime.of(2026, 12, 31, 12, 0, 0, 0, ZONE)
        val LATE_EVENING: ZonedDateTime = ZonedDateTime.of(2026, 12, 31, 23, 59, 59, 999_000_000, ZONE)
        val RUN_DATE: LocalDate = LocalDate.parse("2027-01-01")
        const val SPAN_NAME = "greetings.daily-run"
        const val HALF_MILLIS_NANOS = 500_000L
        const val FIRST_RUN = 1
        const val INTERRUPTING_RUN = 3
        const val THREAD_TIMEOUT_MILLIS = 5_000L
    }
}

package com.simarel.vkbot.greetings.adapter.input.schedule

import com.simarel.vkbot.greetings.test.FakeRunDailyGreetingsInputPort
import com.simarel.vkbot.greetings.testfixtures.FakeGreetingProvider
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.quarkus.scheduler.Scheduled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.time.ZoneId

class DailyGreetingRunnerTest {

    @Test
    fun `the schedule reads the cron and the zone from the greeting keys`() {
        val scheduled = DailyGreetingRunner::class.java
            .getMethod("runScheduled")
            .getAnnotation(Scheduled::class.java)

        assertEquals("daily-greetings", scheduled.identity)
        assertEquals("{greetings.cron}", scheduled.cron)
        assertEquals("{greetings.timezone}", scheduled.timeZone)
    }

    @Test
    fun `a successful run closes the span with OK and the counters`() {
        val exporter = InMemorySpanExporter.create()
        val input = FakeRunDailyGreetingsInputPort {
            FakeGreetingProvider.createRunDailyGreetingsResponse(planned = 2, sent = 1, failed = 1)
        }
        val runner = runner(input = input, exporter = exporter)

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
        val runner = runner(input = input, exporter = exporter)

        val thrown = assertThrows<IllegalStateException> { runner.runOnce(RUN_DATE) }

        assertEquals("collection failed", thrown.message)
        val span = exporter.finishedSpanItems.single()
        assertEquals(setOf("date"), span.attributes.asMap().keys.map { it.key }.toSet())
        assertEquals(StatusCode.ERROR, span.status.statusCode)
    }

    @Test
    fun `an interruption inside the run is not reported as ERROR`() {
        val exporter = InMemorySpanExporter.create()
        val input = FakeRunDailyGreetingsInputPort { throw InterruptedException() }
        val runner = runner(input = input, exporter = exporter)

        assertThrows<InterruptedException> { runner.runOnce(RUN_DATE) }

        val span = exporter.finishedSpanItems.single()
        assertNotEquals(StatusCode.ERROR, span.status.statusCode)
    }

    private fun runner(
        input: FakeRunDailyGreetingsInputPort = FakeRunDailyGreetingsInputPort(),
        exporter: InMemorySpanExporter = InMemorySpanExporter.create(),
    ): DailyGreetingRunner = DailyGreetingRunner(input, tracer(exporter), ZONE.id)

    private fun tracer(exporter: InMemorySpanExporter): Tracer = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
        .build()
        .get("daily-greetings-test")

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Minsk")
        val RUN_DATE: LocalDate = LocalDate.parse("2027-01-01")
        const val SPAN_NAME = "greetings.daily-run"
    }
}

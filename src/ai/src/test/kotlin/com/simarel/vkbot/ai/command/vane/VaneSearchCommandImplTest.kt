package com.simarel.vkbot.ai.command.vane

import com.simarel.vkbot.ai.port.output.vane.VaneSearchOutputPort
import com.simarel.vkbot.ai.port.output.vane.VaneSearchRequest
import com.simarel.vkbot.ai.port.output.vane.VaneSearchResponse
import com.simarel.vkbot.ai.testfixtures.FakeVaneProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.stream.Stream

class VaneSearchCommandImplTest {

    @ParameterizedTest
    @MethodSource("tools")
    fun `execute sends the tool sources and optimization mode to the vane port`(
        request: VaneSearchCommandRequest,
        expectedSources: List<String>,
        expectedOptimizationMode: String,
    ) {
        val port = RecordingVaneSearchOutputPort()

        VaneSearchCommandImpl(port).execute(request)

        val actual = port.requests.single()
        assertEquals(QUERY, actual.query)
        assertEquals(expectedSources, actual.sources)
        assertEquals(expectedOptimizationMode, actual.optimizationMode)
    }

    @ParameterizedTest
    @MethodSource("toolLabels")
    fun `execute formats the numbered sources under the tool source label`(
        request: VaneSearchCommandRequest,
        expectedLabel: String,
    ) {
        val port = RecordingVaneSearchOutputPort(
            response = FakeVaneProvider.createVanePortSearchResponse(
                sources = listOf(
                    FakeVaneProvider.createVanePortSource(),
                    FakeVaneProvider.createVanePortSource(
                        content = "second content",
                        title = "Second source",
                        url = "https://example.com/second",
                    ),
                ),
            ),
        )

        val result = VaneSearchCommandImpl(port).execute(request).result

        assertEquals(
            "Vane answer\n" +
                "\n" +
                "--- $expectedLabel ---\n" +
                "[1] Source title: https://example.com/source\n" +
                "[2] Second source: https://example.com/second\n",
            result,
        )
    }

    @Test
    fun `execute omits the sources block when vane returns no sources`() {
        val port = RecordingVaneSearchOutputPort(
            response = FakeVaneProvider.createVanePortSearchResponse(sources = emptyList()),
        )

        val result = VaneSearchCommandImpl(port).execute(
            FakeVaneProvider.createVaneWebSearchCommandRequest(),
        ).result

        assertEquals("Vane answer\n", result)
    }

    @Test
    fun `execute returns a search failed line instead of throwing when the port fails`() {
        val port = RecordingVaneSearchOutputPort(failure = IllegalStateException("vane is down"))

        val result = VaneSearchCommandImpl(port).execute(
            FakeVaneProvider.createVaneWebSearchCommandRequest(),
        ).result

        assertEquals("Search failed: vane is down", result)
    }

    private class RecordingVaneSearchOutputPort(
        private val response: VaneSearchResponse = FakeVaneProvider.createVanePortSearchResponse(),
        private val failure: RuntimeException? = null,
    ) : VaneSearchOutputPort {
        val requests = ConcurrentLinkedQueue<VaneSearchRequest>()

        override fun execute(request: VaneSearchRequest): VaneSearchResponse {
            requests.add(request)
            failure?.let { throw it }
            return response
        }
    }

    companion object {
        private const val QUERY = "kotlin coroutines"

        @JvmStatic
        fun tools(): Stream<Arguments> = Stream.of(
            Arguments.of(
                FakeVaneProvider.createVaneWebSearchCommandRequest(QUERY),
                listOf("web"),
                "balanced",
                "Sources",
            ),
            Arguments.of(
                FakeVaneProvider.createVaneAcademicSearchCommandRequest(QUERY),
                listOf("academic"),
                "quality",
                "Academic Sources",
            ),
            Arguments.of(
                FakeVaneProvider.createVaneDiscussionSearchCommandRequest(QUERY),
                listOf("discussions"),
                "balanced",
                "Discussion Sources",
            ),
        )

        @JvmStatic
        fun toolLabels(): Stream<Arguments> = tools().map { Arguments.of(it.get()[0], it.get()[3]) }
    }
}

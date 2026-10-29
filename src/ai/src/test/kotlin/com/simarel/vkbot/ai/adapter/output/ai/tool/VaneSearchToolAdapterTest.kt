package com.simarel.vkbot.ai.adapter.output.ai.tool

import com.simarel.vkbot.ai.command.vane.VaneSearchCommand
import com.simarel.vkbot.ai.command.vane.VaneSearchCommandRequest
import com.simarel.vkbot.ai.command.vane.VaneSearchCommandResponse
import com.simarel.vkbot.ai.testfixtures.FakeVaneProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.stream.Stream

class VaneSearchToolAdapterTest {

    @ParameterizedTest
    @MethodSource("toolCalls")
    fun `invoking a tool delegates to VaneSearchCommand with the matching request and returns its result`(
        tool: (VaneSearchToolAdapter, String) -> String,
        expectedRequest: VaneSearchCommandRequest,
        expectedResult: String,
    ) {
        val command = RecordingVaneSearchCommand(expectedResult)

        val result = tool(VaneSearchToolAdapter(command), QUERY)

        assertEquals(expectedRequest, command.requests.single())
        assertEquals(expectedResult, result)
    }

    private class RecordingVaneSearchCommand(
        private val result: String = "Vane answer",
    ) : VaneSearchCommand {
        val requests = ConcurrentLinkedQueue<VaneSearchCommandRequest>()

        override fun execute(request: VaneSearchCommandRequest): VaneSearchCommandResponse {
            requests.add(request)
            return FakeVaneProvider.createVaneSearchCommandResponse(result)
        }
    }

    companion object {
        private const val QUERY = "kotlin coroutines"

        @JvmStatic
        fun toolCalls(): Stream<Arguments> = Stream.of(
            Arguments.of(
                { adapter: VaneSearchToolAdapter, query: String -> adapter.search_web(query) },
                FakeVaneProvider.createVaneWebSearchCommandRequest(QUERY),
                "web result",
            ),
            Arguments.of(
                { adapter: VaneSearchToolAdapter, query: String -> adapter.search_academic(query) },
                FakeVaneProvider.createVaneAcademicSearchCommandRequest(QUERY),
                "academic result",
            ),
            Arguments.of(
                { adapter: VaneSearchToolAdapter, query: String -> adapter.search_discussions(query) },
                FakeVaneProvider.createVaneDiscussionSearchCommandRequest(QUERY),
                "discussion result",
            ),
        )
    }
}

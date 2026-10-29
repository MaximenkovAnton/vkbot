package com.simarel.vkbot.ai.usecase.summary

import com.simarel.vkbot.ai.command.summary.GenerateSummaryCommand
import com.simarel.vkbot.ai.testfixtures.FakeSummaryProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

private const val PEER_ID = 2_000_000_001L
private const val FIRST_MESSAGE_ID = 10L
private const val LAST_MESSAGE_ID = 20L
private val SUMMARY_ID: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000bb")

class GenerateSummaryUsecaseTest {

    @Test
    fun `execute passes the identifiers to the command and returns its summary id`() {
        val command = RecordingGenerateSummaryCommand(
            FakeSummaryProvider.createGenerateSummaryCommandResponse(SUMMARY_ID),
        )
        val usecase = GenerateSummaryUsecase(command)

        val response = usecase.execute(
            FakeSummaryProvider.createGenerateSummaryInputPortRequest(
                peerId = PEER_ID,
                firstMessageId = FIRST_MESSAGE_ID,
                lastMessageId = LAST_MESSAGE_ID,
            ),
        )

        assertEquals(
            FakeSummaryProvider.createGenerateSummaryCommandRequest(
                peerId = PEER_ID,
                firstMessageId = FIRST_MESSAGE_ID,
                lastMessageId = LAST_MESSAGE_ID,
            ),
            command.requests.single(),
        )
        assertEquals(SUMMARY_ID, response.summaryId)
    }

    private class RecordingGenerateSummaryCommand(
        private val response: GenerateSummaryCommand.GenerateSummaryResponse,
    ) : GenerateSummaryCommand {
        val requests = ConcurrentLinkedQueue<GenerateSummaryCommand.GenerateSummaryRequest>()

        override fun execute(
            request: GenerateSummaryCommand.GenerateSummaryRequest,
        ): GenerateSummaryCommand.GenerateSummaryResponse {
            requests.add(request)
            return response
        }
    }
}

package com.simarel.vkbot.ai.usecase.summary

import com.simarel.vkbot.ai.command.summary.ProcessSummaryCommand
import com.simarel.vkbot.ai.port.input.summary.ProcessSummaryInputPortResponse
import com.simarel.vkbot.ai.testfixtures.FakeSummaryProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

private const val PEER_ID = 2_000_000_001L
private const val CONVERSATION_MESSAGE_ID = 7_777L

class ProcessSummaryUsecaseTest {

    @Test
    fun `execute passes the identifiers to the command and returns the response`() {
        val command = RecordingProcessSummaryCommand()
        val usecase = ProcessSummaryUsecase(command)

        val response = usecase.execute(
            FakeSummaryProvider.createProcessSummaryInputPortRequest(
                peerId = PEER_ID,
                conversationMessageId = CONVERSATION_MESSAGE_ID,
            ),
        )

        assertEquals(
            FakeSummaryProvider.createProcessSummaryCommandRequest(
                peerId = PEER_ID,
                conversationMessageId = CONVERSATION_MESSAGE_ID,
            ),
            command.requests.single(),
        )
        assertEquals(ProcessSummaryInputPortResponse, response)
    }

    private class RecordingProcessSummaryCommand : ProcessSummaryCommand {
        val requests = ConcurrentLinkedQueue<ProcessSummaryCommand.ProcessSummaryRequest>()

        override fun execute(
            request: ProcessSummaryCommand.ProcessSummaryRequest,
        ): ProcessSummaryCommand.ProcessSummaryResponse {
            requests.add(request)
            return ProcessSummaryCommand.ProcessSummaryResponse
        }
    }
}

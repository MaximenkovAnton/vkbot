package com.simarel.vkbot.ai.usecase.message.requireAnswer

import com.simarel.vkbot.ai.command.answer.GenerateAnswerCommand
import com.simarel.vkbot.ai.command.answer.GenerateAnswerCommandRequest
import com.simarel.vkbot.ai.command.answer.GenerateAnswerCommandResponse
import com.simarel.vkbot.ai.testfixtures.FakeAnswerProvider
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.ResponseMessage
import com.simarel.vkbot.testfixtures.command.vkFacade.FakePublishEventCommand
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

class GenerateAnswerUsecaseTest {

    @Test
    fun `execute publishes ANSWER_MESSAGE with the generated answer addressed to the original message`() {
        val message = FakeVoProvider.createMessage()
        val generatedAnswer = FakeVoProvider.createMessageText("generated answer")
        val generateAnswerCommand = RecordingGenerateAnswerCommand(
            FakeAnswerProvider.createGenerateAnswerCommandResponse(generatedAnswer),
        )
        val publishEventCommand = FakePublishEventCommand()
        val usecase = GenerateAnswerUsecase(generateAnswerCommand, publishEventCommand)
        val request = FakeAnswerProvider.createMessageRequireAnswerInputPortRequest(message)

        val response = usecase.execute(request)

        assertEquals("ok", response.value)
        assertEquals(
            FakeAnswerProvider.createGenerateAnswerCommandRequest(message),
            generateAnswerCommand.requests.single(),
        )
        val published = publishEventCommand.executeCalls.single()
        assertEquals(Event.ANSWER_MESSAGE, published.event)
        val publishedPayload = published.payload.value as ResponseMessage
        assertEquals(generatedAnswer, publishedPayload.messageText)
        assertEquals(message, publishedPayload.responseTo)
    }

    private class RecordingGenerateAnswerCommand(
        private val response: GenerateAnswerCommandResponse,
    ) : GenerateAnswerCommand {
        val requests = ConcurrentLinkedQueue<GenerateAnswerCommandRequest>()

        override fun execute(request: GenerateAnswerCommandRequest): GenerateAnswerCommandResponse {
            requests.add(request)
            return response
        }
    }
}

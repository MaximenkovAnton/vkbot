package com.simarel.vkbot.processor.usecase.message.messageReceived

import com.simarel.vkbot.processor.command.isRequireAnswer.IsRequireAnswerCommand
import com.simarel.vkbot.processor.command.isRequireAnswer.IsRequireAnswerCommand.IsRequireAnswerRequest
import com.simarel.vkbot.processor.command.isSpam.IsSpamCommand
import com.simarel.vkbot.processor.command.isSpam.IsSpamCommand.IsSpamRequest
import com.simarel.vkbot.processor.domain.decision.DecisionAnalysis
import com.simarel.vkbot.processor.port.input.messageReceived.MessageReceivedInputPort
import com.simarel.vkbot.processor.port.input.messageReceived.MessageReceivedInputPortRequest
import com.simarel.vkbot.processor.port.input.messageReceived.MessageReceivedInputPortResponse
import com.simarel.vkbot.processor.port.output.decision.AnalyzeMessageOutputPort
import com.simarel.vkbot.processor.port.output.decision.AnalyzeMessageRequest
import com.simarel.vkbot.share.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.share.command.publishEvent.PublishEventRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.ResponseMessage
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.Payload
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
open class MessageReceivedUsecaseInput(
    private val publishEventCommand: PublishEventCommand,
    private val isRequireAnswerCommand: IsRequireAnswerCommand,
    private val analyzeMessageOutputPort: AnalyzeMessageOutputPort,
    private val isSpamCommand: IsSpamCommand,
    @ConfigProperty(name = "decision.spam-reply.enabled", defaultValue = "true")
    private val spamReplyEnabled: Boolean,
) : MessageReceivedInputPort {
    private val okResponse = MessageReceivedInputPortResponse("ok")
    private val logger: System.Logger = System.getLogger(MessageReceivedUsecaseInput::class.java.name)

    override fun execute(request: MessageReceivedInputPortRequest): MessageReceivedInputPortResponse {
        /*
        TODO: Добавить контекст
        1) сохранить сообщение в бд
        2) сохранить сообщение в rag
        3) достать подходящие сообщения из rag
        4) достать n последних сообщений из бд
        5) использовать данные как контекст для ответа на вопрос
         */
        // TODO: Собирать профиль пользователей на основе сообщений в чатах
        val message = request.message
        if (spamReplyEnabled && message.fromId.isHuman() && message.peerId.isGroupChat()) {
            val analysis = analyze(message) ?: return requireAnswer(message)
            if (isSpamCommand.execute(IsSpamRequest(analysis.spamProbability)).isSpam) {
                publishEventCommand.execute(
                    PublishEventRequest(
                        event = Event.ANSWER_MESSAGE,
                        payload = Payload(
                            ResponseMessage(
                                messageText = MessageText.of(spamNotification(analysis.spamProbability)),
                                responseTo = message,
                            ),
                        ),
                    ),
                )
                return okResponse
            }
        }
        return requireAnswer(message)
    }

    private fun requireAnswer(message: Message): MessageReceivedInputPortResponse {
        val requireAnswerResult = isRequireAnswerCommand.execute(
            IsRequireAnswerRequest(message),
        )
        if (requireAnswerResult.requiresAnswer) {
            publishEventCommand.execute(
                PublishEventRequest(
                    event = Event.MESSAGE_REQUIRE_ANSWER,
                    payload = Payload(message),
                ),
            )
        }
        return okResponse
    }

    @Suppress("TooGenericExceptionCaught")
    private fun analyze(message: Message): DecisionAnalysis? = try {
        analyzeMessageOutputPort.execute(AnalyzeMessageRequest(message.messageText)).analysis
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw e
    } catch (e: RuntimeException) {
        logger.log(
            System.Logger.Level.WARNING,
            "Decision model failed for peerId=${message.peerId.value}: ${e.javaClass.simpleName}",
        )
        null
    }

    private fun spamNotification(spamProbability: Double): String {
        val percent = Math.round(spamProbability * PERCENT_SCALE)
        return "Сработал автодетект спама. Вероятность спама: $percent%."
    }

    private companion object {
        const val PERCENT_SCALE = 100
    }
}

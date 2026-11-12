package com.simarel.vkbot.processor.usecase.message.messageReceived

import com.simarel.vkbot.processor.command.help.BuildHelpTextCommand
import com.simarel.vkbot.processor.command.help.BuildHelpTextCommand.BuildHelpTextRequest
import com.simarel.vkbot.processor.command.isJailbreak.IsJailbreakCommand
import com.simarel.vkbot.processor.command.isJailbreak.IsJailbreakCommand.IsJailbreakRequest
import com.simarel.vkbot.processor.command.isRequireAnswer.IsRequireAnswerCommand
import com.simarel.vkbot.processor.command.isRequireAnswer.IsRequireAnswerCommand.IsRequireAnswerRequest
import com.simarel.vkbot.processor.command.isSpam.IsSpamCommand
import com.simarel.vkbot.processor.command.isSpam.IsSpamCommand.IsSpamRequest
import com.simarel.vkbot.processor.command.knowledge.RequestKnowledgeExtractionCommand
import com.simarel.vkbot.processor.command.resolveIntent.ResolveMessageIntentCommand
import com.simarel.vkbot.processor.command.resolveIntent.ResolveMessageIntentCommand.ResolveMessageIntentRequest
import com.simarel.vkbot.processor.domain.decision.DecisionAnalysis
import com.simarel.vkbot.processor.domain.decision.MessageIntent
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
@Suppress("LongParameterList")
open class MessageReceivedUsecaseInput(
    private val publishEventCommand: PublishEventCommand,
    private val isRequireAnswerCommand: IsRequireAnswerCommand,
    private val analyzeMessageOutputPort: AnalyzeMessageOutputPort,
    private val isSpamCommand: IsSpamCommand,
    private val isJailbreakCommand: IsJailbreakCommand,
    private val buildHelpTextCommand: BuildHelpTextCommand,
    private val resolveMessageIntentCommand: ResolveMessageIntentCommand,
    private val requestKnowledgeExtractionCommand: RequestKnowledgeExtractionCommand,
    @ConfigProperty(name = "decision.spam-reply.enabled", defaultValue = "true")
    private val spamReplyEnabled: Boolean,
    @ConfigProperty(name = "decision.jailbreak-reply.enabled", defaultValue = "true")
    private val jailbreakReplyEnabled: Boolean,
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
        triggerKnowledgeExtraction(message)
        val analysis = decisionStage(message)
        if (analysis != null) {
            if (spamReplyEnabled && isSpamCommand.execute(IsSpamRequest(analysis.spamProbability)).isSpam) {
                publishAnswer(message, MessageText.of(spamNotification(analysis.spamProbability)))
                return okResponse
            }
            if (jailbreakReplyEnabled &&
                isJailbreakCommand.execute(IsJailbreakRequest(analysis.jailbreakProbability)).isJailbreak
            ) {
                publishAnswer(message, MessageText.of(jailbreakNotification(analysis.jailbreakProbability)))
                return okResponse
            }
        }
        if (!isRequireAnswerCommand.execute(IsRequireAnswerRequest(message)).requiresAnswer) {
            return okResponse
        }
        when (resolveIntent(message, analysis)) {
            MessageIntent.HELP -> publishHelp(message)
            MessageIntent.NEWS -> publish(message, Event.NEWS_DIGEST_REQUESTED)
            MessageIntent.PROFILE -> publish(message, Event.PROFILE_REQUESTED)
            MessageIntent.GENERAL -> publish(message, Event.MESSAGE_REQUIRE_ANSWER)
        }
        return okResponse
    }

    private fun triggerKnowledgeExtraction(message: Message) {
        try {
            requestKnowledgeExtractionCommand.execute(RequestKnowledgeExtractionCommand.Request(message))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        }
    }

    private fun decisionStage(message: Message): DecisionAnalysis? {
        val branchesEnabled = spamReplyEnabled || jailbreakReplyEnabled
        if (!branchesEnabled || !message.fromId.isHuman() || !message.peerId.isGroupChat()) {
            return null
        }
        return analyze(message)
    }

    private fun resolveIntent(message: Message, analysis: DecisionAnalysis?): MessageIntent {
        val request = ResolveMessageIntentRequest(message.messageText)
        return resolveMessageIntentCommand.execute(request).intent
            ?: analysis?.intent
            ?: analyze(message)?.intent
            ?: MessageIntent.GENERAL
    }

    private fun publishHelp(message: Message) {
        val helpText = buildHelpTextCommand.execute(BuildHelpTextRequest)
        publishAnswer(message, helpText.messageText)
    }

    private fun publishAnswer(message: Message, messageText: MessageText) {
        publishEventCommand.execute(
            PublishEventRequest(
                event = Event.ANSWER_MESSAGE,
                payload = Payload(ResponseMessage(messageText = messageText, responseTo = message)),
            ),
        )
    }

    private fun publish(message: Message, event: Event) {
        publishEventCommand.execute(PublishEventRequest(event = event, payload = Payload(message)))
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

    private fun jailbreakNotification(jailbreakProbability: Double): String {
        val percent = Math.round(jailbreakProbability * PERCENT_SCALE)
        return "Сработал автодетект попытки взлома ИИ. Вероятность: $percent%."
    }

    private companion object {
        const val PERCENT_SCALE = 100
    }
}

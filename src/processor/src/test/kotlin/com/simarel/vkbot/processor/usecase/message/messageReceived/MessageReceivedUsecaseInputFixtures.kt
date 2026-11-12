package com.simarel.vkbot.processor.usecase.message.messageReceived

import com.simarel.vkbot.processor.command.help.BuildHelpTextCommand
import com.simarel.vkbot.processor.command.help.BuildHelpTextCommandImpl
import com.simarel.vkbot.processor.command.isJailbreak.IsJailbreakCommandImpl
import com.simarel.vkbot.processor.command.isSpam.IsSpamCommandImpl
import com.simarel.vkbot.processor.command.knowledge.RequestKnowledgeExtractionCommandImpl
import com.simarel.vkbot.processor.command.resolveIntent.ResolveMessageIntentCommandImpl
import com.simarel.vkbot.processor.domain.decision.MessageIntent
import com.simarel.vkbot.processor.testfixtures.command.FakeIsRequireAnswerCommand
import com.simarel.vkbot.processor.testfixtures.decision.FakeDecisionProvider
import com.simarel.vkbot.processor.testfixtures.port.output.decision.FakeAnalyzeMessageOutputPort
import com.simarel.vkbot.processor.testfixtures.port.output.knowledge.FakeHasPendingKnowledgeBatchPort
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.vo.FromId
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.testfixtures.command.vkFacade.FakePublishEventCommand
import com.simarel.vkbot.testfixtures.domain.FakeMessageProvider
import com.simarel.vkbot.testfixtures.domain.FakeVoProvider
import java.time.Duration

internal const val GROUP_CHAT_PEER_ID = 2_000_000_001L
internal const val PRIVATE_CHAT_PEER_ID = 123_456_789L
internal const val TEST_BOT_MENTION = "@testbot"
internal const val TEST_BOT_ID = 9_000_001L
internal val KNOWLEDGE_BATCH_INTERVAL: Duration = Duration.ofMinutes(30)
internal const val KNOWLEDGE_BATCH_SIZE = 25
internal const val JAILBREAK_NOTICE = "Сработал автодетект попытки взлома ИИ. Вероятность: "
internal const val LOGGER_NAME =
    "com.simarel.vkbot.processor.usecase.message.messageReceived.MessageReceivedUsecaseInput"

@Suppress("LongParameterList")
internal fun createUsecase(
    publishCommand: FakePublishEventCommand = FakePublishEventCommand(),
    analyzePort: FakeAnalyzeMessageOutputPort = FakeAnalyzeMessageOutputPort(),
    requiresAnswer: Boolean = true,
    spamThreshold: Double = 0.9,
    spamReplyEnabled: Boolean = true,
    jailbreakThreshold: Double = 0.9,
    jailbreakReplyEnabled: Boolean = true,
    hasPendingKnowledgeBatch: FakeHasPendingKnowledgeBatchPort = FakeHasPendingKnowledgeBatchPort(),
    knowledgeEnabled: Boolean = true,
    knowledgeExcludedChatIds: String = "",
): MessageReceivedUsecaseInput {
    val isRequireAnswerCommand = FakeIsRequireAnswerCommand()
    isRequireAnswerCommand.shouldRequireAnswer = requiresAnswer
    return MessageReceivedUsecaseInput(
        publishCommand,
        isRequireAnswerCommand,
        analyzePort,
        IsSpamCommandImpl(spamThreshold),
        IsJailbreakCommandImpl(jailbreakThreshold),
        BuildHelpTextCommandImpl(TEST_BOT_MENTION),
        ResolveMessageIntentCommandImpl(),
        RequestKnowledgeExtractionCommandImpl(
            publishEventCommand = publishCommand,
            hasPendingKnowledgeBatchPort = hasPendingKnowledgeBatch,
            enabled = knowledgeEnabled,
            batchSize = KNOWLEDGE_BATCH_SIZE,
            batchInterval = KNOWLEDGE_BATCH_INTERVAL,
            excludedChatIdsConfig = knowledgeExcludedChatIds,
            botId = TEST_BOT_ID,
        ),
        spamReplyEnabled,
        jailbreakReplyEnabled,
    )
}

internal fun helpText(): MessageText = BuildHelpTextCommandImpl(TEST_BOT_MENTION).execute(
    BuildHelpTextCommand.BuildHelpTextRequest,
).messageText

internal fun incomingMessage(
    fromId: FromId = FakeVoProvider.createHumanFromId(),
    peerId: PeerId = FakeVoProvider.createPeerId(GROUP_CHAT_PEER_ID),
    messageText: MessageText = FakeVoProvider.createMessageText("Что нового в чате?"),
): Message = FakeMessageProvider.createMessage(
    fromId = fromId,
    peerId = peerId,
    messageText = messageText,
    forwardedMessages = emptyList(),
)

internal fun analysis(
    spamProbability: Double,
    intent: MessageIntent = MessageIntent.GENERAL,
    jailbreakProbability: Double = 0.0,
) = FakeDecisionProvider.createAnalyzeMessageResponse(
    FakeDecisionProvider.createAnalysis(
        spamProbability = spamProbability,
        jailbreakProbability = jailbreakProbability,
        intent = intent,
    ),
)

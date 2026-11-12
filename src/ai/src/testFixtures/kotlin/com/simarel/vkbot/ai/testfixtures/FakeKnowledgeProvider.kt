package com.simarel.vkbot.ai.testfixtures

import com.simarel.vkbot.ai.command.knowledge.GenerateChatKnowledgeSummaryCommand
import com.simarel.vkbot.ai.command.knowledge.GenerateUserKnowledgeSummaryCommand
import com.simarel.vkbot.ai.command.knowledge.ProcessUserKnowledgeCommand
import com.simarel.vkbot.ai.domain.knowledge.KnowledgeFactDraft
import com.simarel.vkbot.ai.port.input.knowledge.ProcessChatKnowledgeSummaryInputPortRequest
import com.simarel.vkbot.ai.port.input.knowledge.ProcessChatKnowledgeSummaryInputPortResponse
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeInputPortRequest
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeInputPortResponse
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeSummaryInputPortRequest
import com.simarel.vkbot.ai.port.input.knowledge.ProcessUserKnowledgeSummaryInputPortResponse
import com.simarel.vkbot.ai.port.output.knowledge.ChatKnowledgeSummaryPort
import com.simarel.vkbot.ai.port.output.knowledge.KnowledgeExtractionRequest
import com.simarel.vkbot.ai.port.output.knowledge.KnowledgeExtractionResponse
import com.simarel.vkbot.ai.port.output.knowledge.UserKnowledgeSummaryPort
import com.simarel.vkbot.share.domain.event.ChatKnowledgeSummaryRequestedPayload
import com.simarel.vkbot.share.domain.event.UserKnowledgeRequestedPayload
import com.simarel.vkbot.share.domain.event.UserKnowledgeSummaryRequestedPayload
import com.simarel.vkbot.share.domain.model.StoredMessage
import com.simarel.vkbot.share.domain.model.knowledge.ChatKnowledge
import com.simarel.vkbot.share.port.output.persistence.FindChatKnowledgeSummaryPort
import com.simarel.vkbot.share.port.output.persistence.FindChatSummaryMessagesPort
import com.simarel.vkbot.testfixtures.domain.FakeEventProvider

@Suppress("TooManyFunctions")
object FakeKnowledgeProvider {

    fun createKnowledgeExtractionRequest(
        messages: List<String> = listOf("[7600001|Иван Петров]: люблю футбол"),
    ) = KnowledgeExtractionRequest(messages = messages)

    fun createKnowledgeExtractionResponse(
        facts: List<KnowledgeFactDraft> = emptyList(),
    ) = KnowledgeExtractionResponse(facts = facts)

    @Suppress("LongParameterList")
    fun createKnowledgeFactDraft(
        subjectId: Long? = 7600001,
        subjectName: String? = null,
        category: String? = "INTEREST",
        key: String? = "interest_football",
        value: String? = "футбол",
        valuePayload: Map<String, Any?>? = emptyMap(),
        confidence: Double? = 0.9,
        sourceKind: String? = "SELF_STATEMENT",
        sourceConversationMessageId: Long? = 101,
    ) = KnowledgeFactDraft(
        subjectId = subjectId,
        subjectName = subjectName,
        category = category,
        key = key,
        value = value,
        valuePayload = valuePayload,
        confidence = confidence,
        sourceKind = sourceKind,
        sourceConversationMessageId = sourceConversationMessageId,
    )

    fun createProcessUserKnowledgeCommandRequest(
        payload: UserKnowledgeRequestedPayload = FakeEventProvider.createUserKnowledgeRequestedPayload(),
    ) = ProcessUserKnowledgeCommand.Request(payload = payload)

    fun createProcessUserKnowledgeInputPortRequest(
        payload: UserKnowledgeRequestedPayload = FakeEventProvider.createUserKnowledgeRequestedPayload(),
    ) = ProcessUserKnowledgeInputPortRequest(payload = payload)

    fun createProcessUserKnowledgeInputPortResponse() = ProcessUserKnowledgeInputPortResponse

    fun createFindChatKnowledgeSummaryRequest(
        peerId: Long = 2_000_000_001,
    ) = FindChatKnowledgeSummaryPort.Request(peerId = peerId)

    fun createFindChatKnowledgeSummaryResponse(
        knowledge: ChatKnowledge? = null,
    ) = FindChatKnowledgeSummaryPort.Response(knowledge = knowledge)

    fun createFindChatSummaryMessagesRequest(
        peerId: Long = 2_000_000_001,
        limit: Int = 100,
    ) = FindChatSummaryMessagesPort.Request(peerId = peerId, limit = limit)

    fun createFindChatSummaryMessagesResponse(
        messages: List<StoredMessage> = emptyList(),
    ) = FindChatSummaryMessagesPort.Response(messages = messages)

    fun createChatKnowledgeSummaryRequest(
        messages: List<String> = listOf("[Иван Петров]: люблю футбол"),
    ) = ChatKnowledgeSummaryPort.Request(messages = messages)

    fun createChatKnowledgeSummaryResponse(
        text: String = "Темы: программирование, мемы и шутки.",
    ) = ChatKnowledgeSummaryPort.Response(text = text)

    fun createGenerateChatKnowledgeSummaryCommandRequest(
        payload: ChatKnowledgeSummaryRequestedPayload = FakeEventProvider.createChatKnowledgeSummaryRequestedPayload(),
    ) = GenerateChatKnowledgeSummaryCommand.Request(payload = payload)

    fun createProcessChatKnowledgeSummaryInputPortRequest(
        payload: ChatKnowledgeSummaryRequestedPayload = FakeEventProvider.createChatKnowledgeSummaryRequestedPayload(),
    ) = ProcessChatKnowledgeSummaryInputPortRequest(payload = payload)

    fun createProcessChatKnowledgeSummaryInputPortResponse() = ProcessChatKnowledgeSummaryInputPortResponse

    fun createProcessUserKnowledgeSummaryInputPortRequest(
        payload: UserKnowledgeSummaryRequestedPayload = FakeEventProvider.createUserKnowledgeSummaryRequestedPayload(),
    ) = ProcessUserKnowledgeSummaryInputPortRequest(payload = payload)

    fun createProcessUserKnowledgeSummaryInputPortResponse() = ProcessUserKnowledgeSummaryInputPortResponse

    fun createGenerateUserKnowledgeSummaryCommandRequest(
        payload: UserKnowledgeSummaryRequestedPayload = FakeEventProvider.createUserKnowledgeSummaryRequestedPayload(),
    ) = GenerateUserKnowledgeSummaryCommand.Request(payload = payload)

    fun createUserKnowledgeSummaryRequest(
        facts: String = "Интересы и увлечения:\n- футбол",
    ) = UserKnowledgeSummaryPort.Request(facts = facts)

    fun createUserKnowledgeSummaryResponse(
        text: String = "Иван увлекается футболом и историей.",
    ) = UserKnowledgeSummaryPort.Response(text = text)
}

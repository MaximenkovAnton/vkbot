package com.simarel.vkbot.vkFacade.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.model.ResponseMessage
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.port.input.EventProcessor
import com.simarel.vkbot.vkFacade.domain.ForwardedMessages
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputPort
import com.simarel.vkbot.vkFacade.port.input.vk.VkSendMessageInputRequest
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.time.ZoneOffset

@ApplicationScoped
class SummaryReadyEventProcessor(
    private val objectMapper: ObjectMapper,
    private val vkSendMessageInputPort: VkSendMessageInputPort,
) : EventProcessor {

    override fun process(jsonString: String) {
        val payload = objectMapper.readValue(jsonString, SummaryReadyPayload::class.java)

        val forwardedMessages = ForwardedMessages(
            peer_id = payload.peerId,
            conversation_message_ids = listOf(
                payload.firstConversationMessageId,
                payload.lastConversationMessageId
            ),
            is_reply = false
        )

        val syntheticMessage = Message.of(
            groupId = null,
            date = Instant.now().atOffset(ZoneOffset.UTC),
            fromId = null,
            peerId = payload.peerId,
            conversationMessageId = null,
            messageText = payload.messageText,
            forwardedMessages = emptyList()
        )

        vkSendMessageInputPort.execute(
            VkSendMessageInputRequest(
                responseMessage = ResponseMessage(
                    messageText = MessageText.of(payload.messageText),
                    responseTo = syntheticMessage
                ),
                forwardedMessages = forwardedMessages
            )
        )
    }

    override fun event() = Event.SUMMARY_READY

    data class SummaryReadyPayload(
        val peerId: Long,
        val messageText: String,
        val firstConversationMessageId: Long,
        val lastConversationMessageId: Long,
    )
}

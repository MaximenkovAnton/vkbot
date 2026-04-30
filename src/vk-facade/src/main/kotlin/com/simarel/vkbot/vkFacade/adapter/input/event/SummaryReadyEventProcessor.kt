package com.simarel.vkbot.vkFacade.adapter.input.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.vo.MessageText
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.port.input.EventProcessor
import com.simarel.vkbot.vkFacade.command.sendVkMessage.SendVkMessageCommand
import com.simarel.vkbot.vkFacade.command.sendVkMessage.SendVkMessageCommandRequest
import com.simarel.vkbot.vkFacade.domain.ForwardedMessages
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class SummaryReadyEventProcessor(
    private val objectMapper: ObjectMapper,
    private val sendVkMessageCommand: SendVkMessageCommand,
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

        sendVkMessageCommand.execute(
            SendVkMessageCommandRequest(
                peerId = PeerId.of(payload.peerId),
                message = MessageText.of(payload.messageText),
                forwardedMessages = forwardedMessages,
                rand = 0
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

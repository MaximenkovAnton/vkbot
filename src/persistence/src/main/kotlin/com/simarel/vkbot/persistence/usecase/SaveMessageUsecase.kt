package com.simarel.vkbot.persistence.usecase

import com.fasterxml.jackson.databind.ObjectMapper
import com.simarel.vkbot.persistence.command.fetchuserprofile.FetchUserProfileCommand
import com.simarel.vkbot.persistence.command.fetchuserprofile.FetchUserProfileRequest
import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.persistence.port.output.persistence.MessageRepositoryPort
import com.simarel.vkbot.share.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.share.command.publishEvent.PublishEventRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.vo.Payload
import com.simarel.vkbot.share.domain.vo.PeerId
import com.simarel.vkbot.share.domain.vo.ConversationMessageId
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
open class SaveMessageUsecase(
    private val messageRepositoryPort: MessageRepositoryPort,
    private val objectMapper: ObjectMapper,
    private val publishEventCommand: PublishEventCommand,
    private val fetchUserProfileCommand: FetchUserProfileCommand,
) {
    @Transactional
    open fun execute(message: Message) {
        fetchUserProfileCommand.execute(FetchUserProfileRequest(message))
        saveMessage(message)
        publishEventCommand.execute(
            PublishEventRequest(
                event = Event.MESSAGE_RECEIVED,
                payload = Payload(message),
            ),
        )
        publishEventCommand.execute(
            PublishEventRequest(
                event = Event.SUMMARY_REQUESTED,
                payload = Payload(
                    SummaryRequestedPayload(
                        peerId = message.peerId,
                        conversationMessageId = message.conversationMessageId
                    )
                ),
            ),
        )
    }

    data class SummaryRequestedPayload(
        val peerId: PeerId,
        val conversationMessageId: ConversationMessageId
    )

    private fun saveMessage(message: Message) {
        val entity = MessageEntity().apply {
            id = UUID.randomUUID()
            date = message.date.value
            fromId = message.fromId.value
            groupId = message.groupId.value
            peerId = message.peerId.value
            conversationMessageId = message.conversationMessageId.value
            messageText = message.messageText.value
            forwardedMessages = if (message.forwardedMessages.isEmpty()) {
                null
            } else {
                objectMapper.writeValueAsString(message.forwardedMessages)
            }
        }
        messageRepositoryPort.save(entity)
    }
}

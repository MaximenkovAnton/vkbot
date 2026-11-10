package com.simarel.vkbot.persistence.usecase

import com.simarel.vkbot.persistence.command.fetchuserprofile.FetchUserProfileCommand
import com.simarel.vkbot.persistence.command.fetchuserprofile.FetchUserProfileRequest
import com.simarel.vkbot.persistence.domain.entity.MessageEntity
import com.simarel.vkbot.persistence.port.input.SaveMessageInputPort
import com.simarel.vkbot.persistence.port.input.SaveMessageInputPortRequest
import com.simarel.vkbot.persistence.port.input.SaveMessageInputPortResponse
import com.simarel.vkbot.persistence.port.output.ForwardedMessagesSerializationPort
import com.simarel.vkbot.persistence.port.output.persistence.SaveMessagePort
import com.simarel.vkbot.persistence.port.output.persistence.UpdateUserStatisticsPort
import com.simarel.vkbot.share.command.publishEvent.PublishEventCommand
import com.simarel.vkbot.share.command.publishEvent.PublishEventRequest
import com.simarel.vkbot.share.domain.Event
import com.simarel.vkbot.share.domain.event.SummaryRequestedPayload
import com.simarel.vkbot.share.domain.model.Message
import com.simarel.vkbot.share.domain.vo.Payload
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.util.UUID

@ApplicationScoped
open class SaveMessageUsecase(
    private val saveMessagePort: SaveMessagePort,
    private val forwardedMessagesSerializationPort: ForwardedMessagesSerializationPort,
    private val publishEventCommand: PublishEventCommand,
    private val fetchUserProfileCommand: FetchUserProfileCommand,
    private val updateUserStatisticsPort: UpdateUserStatisticsPort,
) : SaveMessageInputPort {
    @Transactional
    override fun execute(request: SaveMessageInputPortRequest): SaveMessageInputPortResponse {
        val message = request.message
        fetchUserProfileCommand.execute(FetchUserProfileRequest(message))
        saveMessage(message)
        updateStatistics(message)
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
                        peerId = message.peerId.value,
                        conversationMessageId = message.conversationMessageId.value,
                    ),
                ),
            ),
        )
        return SaveMessageInputPortResponse
    }

    private fun updateStatistics(message: Message) {
        val text = message.messageText.value
        updateUserStatisticsPort.execute(
            UpdateUserStatisticsPort.Request(
                userId = message.fromId.value,
                peerId = message.peerId.value,
                date = message.date.value,
                messageTextLength = text.codePointCount(0, text.length),
            ),
        )
    }

    private fun saveMessage(message: Message) {
        val entity = MessageEntity().apply {
            id = UUID.randomUUID()
            date = message.date.value
            fromId = message.fromId.value
            groupId = message.groupId.value
            peerId = message.peerId.value
            conversationMessageId = message.conversationMessageId.value
            messageText = message.messageText.value
            forwardedMessages = forwardedMessagesSerializationPort.execute(
                ForwardedMessagesSerializationPort.Request(message.forwardedMessages),
            ).json
        }
        saveMessagePort.execute(SaveMessagePort.SaveMessageRequest(entity))
    }
}
